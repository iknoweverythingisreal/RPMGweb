package com.rpmedia.backend.util;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.model.ItemUnit;
import com.rpmedia.backend.model.UnitStatus;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.repository.ItemUnitRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class CsvDataLoader implements CommandLineRunner {

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final CacheManager cacheManager;

    @Autowired
    public CsvDataLoader(ItemRepository itemRepository, ItemUnitRepository itemUnitRepository,
            CacheManager cacheManager) {
        this.itemRepository = itemRepository;
        this.itemUnitRepository = itemUnitRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public void run(String... args) throws Exception {
        java.io.File seedFlag = new java.io.File("data_seeded.flag");
        if (seedFlag.exists()) {
            System.out.println("ℹ️ Data already seeded. Skipping full re-import.");
            return;
        }

        System.out.println("🔥 Starting Full CSV Data Re-import (Clear + Clean Sync)...");

        // Clear existing data (destructive as requested)
        System.out.println("🗑️ Clearing existing items and units...");
        itemUnitRepository.deleteAll();
        itemRepository.deleteAll();

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

                    // Fresh Import (No upsert needed since we cleared)
                    Item item = new Item();
                    item.setName(name);
                    item.setAvailableQuantity(0);

                    item.setDescription(
                            (record.isMapped("Description") && record.isSet("Description"))
                                    ? record.get("Description").trim()
                                    : "");
                    item.setBrand(
                            (record.isMapped("Brand") && record.isSet("Brand")) ? record.get("Brand").trim() : "");
                    item.setModel(
                            (record.isMapped("Model") && record.isSet("Model")) ? record.get("Model").trim() : "");
                    item.setCategory(detectCategoryFromFile(filePath));
                    item.setUom((record.isMapped("UOM") && record.isSet("UOM")) ? record.get("UOM").trim() : "UNIT");
                    item.setRemark(
                            (record.isMapped("Remark") && record.isSet("Remark")) ? record.get("Remark").trim() : "");
                    item.setStatus(UnitStatus.AVAILABLE);

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
                    item.setTotalQuantity(quantity.intValue());

                    Item savedItem = itemRepository.save(item);

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
        try {
            seedFlag.createNewFile();
            System.out.println("🚩 Data seeding flag created.");
        } catch (Exception e) {
            System.err.println("⚠️ Could not create seeding flag: " + e.getMessage());
        }

        // Evict cache
        if (cacheManager.getCache("items") != null) {
            cacheManager.getCache("items").clear();
            System.out.println("🧹 Cache 'items' cleared.");
        }

        System.out.println("✅ Full Data Sync Complete");
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
