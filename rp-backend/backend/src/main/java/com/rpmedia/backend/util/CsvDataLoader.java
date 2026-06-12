package com.rpmedia.backend.util;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.ItemUnit;
import com.rpmedia.backend.model.UnitStatus;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.repository.ItemUnitRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CsvDataLoader implements CommandLineRunner {

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final EventItemRepository eventItemRepository;
    private final CacheManager cacheManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public CsvDataLoader(ItemRepository itemRepository, ItemUnitRepository itemUnitRepository,
            EventItemRepository eventItemRepository,
            CacheManager cacheManager,
            TransactionTemplate transactionTemplate) {
        this.itemRepository = itemRepository;
        this.itemUnitRepository = itemUnitRepository;
        this.eventItemRepository = eventItemRepository;
        this.cacheManager = cacheManager;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("📦 Checking for inventory consolidation requirements...");

        // 1. Consolidate existing duplicates BEFORE import
        consolidateExistingItems();

        // Check DB status
        if (itemRepository.count() > 0) {
            // Even if we skip import, the consolidation above fixed the mess.
            // But we might want to refresh from CSV anyway to get latest metadata.
            // For now, let's respect the skip if items exist (meaning metadata is likely
            // okay).
            System.out.println("ℹ️ Inventory already populated. Consolidation complete.");
            return;
        }

        System.out.println("🚀 Starting Grouped Data Import from CSV...");

        List<String> files = Arrays.asList(
                "csv/RP Inventory 2024-Sound.csv",
                "csv/RP Inventory 2024-Lighting.csv",
                "csv/RP Inventory 2024-Visual.csv",
                "csv/RP Inventory 2024-LED.csv",
                "csv/RP Inventory 2024-IT.csv");

        for (String filePath : files) {
            System.out.println("📂 Loading " + filePath);
            ClassPathResource resource = new ClassPathResource(filePath);
            if (!resource.exists()) {
                System.out.println("⚠️ File not found: " + filePath);
                continue;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                CSVParser csvParser = CSVFormat.Builder.create()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreHeaderCase(true)
                        .setTrim(true)
                        .setAllowMissingColumnNames(true)
                        .build()
                        .parse(reader);

                for (CSVRecord record : csvParser) {
                    String name = null;
                    if (record.isMapped("Name") && record.isSet("Name")) {
                        name = record.get("Name").trim();
                    } else if (record.isMapped("Description") && record.isSet("Description")) {
                        name = record.get("Description").trim();
                    }

                    if (name == null || name.isEmpty())
                        continue;

                    String brand = (record.isMapped("Brand") && record.isSet("Brand")) ? record.get("Brand").trim()
                            : "";
                    String model = (record.isMapped("Model") && record.isSet("Model")) ? record.get("Model").trim()
                            : "";
                    String description = (record.isMapped("Description") && record.isSet("Description"))
                            ? record.get("Description").trim()
                            : "";
                    String uom = (record.isMapped("UOM") && record.isSet("UOM")) ? record.get("UOM").trim() : "UNIT";
                    String category = detectCategoryFromFile(filePath);

                    BigDecimal quantity = BigDecimal.ZERO;
                    try {
                        String rawQty = (record.isMapped("Quantity") && record.isSet("Quantity"))
                                ? record.get("Quantity").replace(",", "").trim()
                                : "0";
                        if (!rawQty.isEmpty()) {
                            quantity = new BigDecimal(rawQty);
                        }
                    } catch (Exception e) {
                        // ignore invalid qty
                    }

                    // 🔹 UPSERT Strategy: Check if item already created in this run
                    Optional<Item> existing = itemRepository
                            .findFirstByCategoryIgnoreCaseAndBrandIgnoreCaseAndModelIgnoreCaseAndDescriptionIgnoreCaseAndUomIgnoreCase(
                                    category, brand, model, description, uom);

                    Item savedItem;
                    if (existing.isPresent()) {
                        savedItem = existing.get();
                        savedItem.setTotalQuantity(savedItem.getTotalQuantity() + quantity.intValue());
                        savedItem = itemRepository.save(savedItem);
                    } else {
                        Item item = new Item();
                        item.setName(name);
                        item.setBrand(brand);
                        item.setModel(model);
                        item.setDescription(description);
                        item.setCategory(category);
                        item.setUom(uom);
                        item.setTotalQuantity(quantity.intValue());
                        item.setAvailableQuantity(0);
                        item.setStatus(UnitStatus.AVAILABLE);
                        savedItem = itemRepository.save(item);
                    }

                    // Handle Serial (ItemUnit)
                    String serial = null;
                    if (record.isMapped("S/N") && record.isSet("S/N"))
                        serial = record.get("S/N").trim();
                    else if (record.isMapped("Serial") && record.isSet("Serial"))
                        serial = record.get("Serial").trim();

                    if (serial != null && !serial.isBlank()) {
                        ItemUnit unit = new ItemUnit();
                        unit.setItem(savedItem);
                        unit.setSerial(serial);
                        unit.setStatus(UnitStatus.AVAILABLE);
                        unit.setCreatedAt(Instant.now());
                        itemUnitRepository.save(unit);
                    }
                }
            }
        }

        // Create flag file to prevent re-import
        // No need to create flag file anymore
        System.out.println("🚩 Data seeding completed.");

        // Evict cache
        if (cacheManager.getCache("items") != null) {
            cacheManager.getCache("items").clear();
            System.out.println("🧹 Cache 'items' cleared.");
        }

        System.out.println("✅ Full Data Sync Complete");
    }

    private void consolidateExistingItems() {
        transactionTemplate.execute(status -> {
            try {
                System.out.println("🔍 Finding duplicate items for consolidation...");
                List<Item> allItems = itemRepository.findAll();

                Map<String, List<Item>> groups = new HashMap<>();
                for (Item item : allItems) {
                    String cat = item.getCategory() != null ? item.getCategory() : "";
                    String brand = item.getBrand() != null ? item.getBrand() : "";
                    String model = item.getModel() != null ? item.getModel() : "";
                    String desc = item.getDescription() != null ? item.getDescription() : "";
                    String uom = item.getUom() != null ? item.getUom() : "";

                    String key = (cat + "|" + brand + "|" + model + "|" + desc + "|" + uom)
                            .toLowerCase().trim();
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                }

                int mergedCount = 0;
                for (Map.Entry<String, List<Item>> entry : groups.entrySet()) {
                    List<Item> items = entry.getValue();
                    if (items.size() > 1) {
                        Item master = items.get(0);
                        List<Long> subordinates = items.subList(1, items.size()).stream()
                                .map(Item::getId)
                                .collect(Collectors.toList());

                        System.out.println("🔗 Merging " + subordinates.size() + " duplicates into Master: "
                                + master.getName() + " (ID: " + master.getId() + ")");

                        // 1. Relink EventItems
                        eventItemRepository.migrateItem(subordinates, master);

                        // 2. Relink ItemUnits
                        itemUnitRepository.migrateItem(subordinates, master);

                        // 3. Update Master Quantity
                        int extraQty = items.subList(1, items.size()).stream()
                                .mapToInt(i -> i.getTotalQuantity() != null ? i.getTotalQuantity() : 0)
                                .sum();
                        master.setTotalQuantity(
                                (master.getTotalQuantity() != null ? master.getTotalQuantity() : 0) + extraQty);
                        itemRepository.save(master);

                        // 4. Delete Orphans
                        itemRepository.deleteAllById(subordinates);
                        mergedCount += subordinates.size();
                    }
                }
                if (mergedCount > 0) {
                    System.out.println("✅ Consolidation complete. Merged " + mergedCount + " duplicate records.");
                } else {
                    System.out.println("✨ No duplicates found. System is clean.");
                }
            } catch (Exception e) {
                System.err.println("❌ Consolidation failed: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        });
    }

    private String detectCategoryFromFile(String file) {
        if (file.contains("Sound"))
            return "SOUND";
        if (file.contains("Lighting"))
            return "LIGHTING";
        if (file.contains("Visual"))
            return "VISUAL";
        if (file.contains("LED"))
            return "LED";
        if (file.contains("IT"))
            return "IT";
        return "GENERAL";
    }
}
