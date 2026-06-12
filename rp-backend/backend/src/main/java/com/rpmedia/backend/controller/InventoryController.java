package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.service.InventoryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    @Autowired
    private ItemRepository itemRepository;

    /**
     * Endpoint: /api/inventory/availability?startDate=2025-11-06&endDate=2025-11-08
     */
    @Autowired
    private com.rpmedia.backend.service.UnifiedAvailabilityService unifiedAvailabilityService;

    /**
     * Endpoint: /api/inventory/availability?startDate=2025-11-06&endDate=2025-11-08
     */
    @GetMapping("/availability")
    public List<Map<String, Object>> getAvailability(
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "excludeEventId", required = false) Long excludeEventId) {

        List<Item> allItems = itemRepository.findAll();
        List<Long> itemIds = allItems.stream()
                .filter(item -> {
                    boolean hasBrand = item.getBrand() != null && !item.getBrand().trim().isEmpty();
                    boolean hasModel = item.getModel() != null && !item.getModel().trim().isEmpty();
                    boolean hasName = item.getName() != null && !item.getName().trim().isEmpty()
                            && !item.getName().equalsIgnoreCase("Unknown Item");
                    return hasBrand || hasModel || hasName;
                })
                .map(Item::getId)
                .toList();

        var availabilityResults = unifiedAvailabilityService.computeBulk(itemIds, excludeEventId, startDate, endDate);

        // Pre-fetch items to avoid N+1 queries inside the stream
        Map<Long, Item> itemMap = new HashMap<>();
        if (!availabilityResults.isEmpty()) {
            List<Long> resultIds = availabilityResults.stream().map(dto -> dto.getItemId()).toList();
            List<Item> items = itemRepository.findAllById(resultIds);
            for (Item it : items) {
                itemMap.put(it.getId(), it);
            }
        }

        return availabilityResults.stream().map(dto -> {
            Map<String, Object> row = new HashMap<>();
            row.put("itemId", dto.getItemId());
            row.put("itemName", dto.getItemName());

            Item item = itemMap.get(dto.getItemId());
            if (item != null) {
                row.put("category", item.getCategory());
                row.put("brand", item.getBrand());
                row.put("model", item.getModel());
                row.put("uom", item.getUom());
            }

            row.put("totalQuantity", dto.getTotal());
            row.put("allocated", dto.getAllocated());
            row.put("available", Math.max(0, dto.getAvailable()));

            return row;
        }).toList();
    }

    @Autowired
    private InventoryService inventoryService;

    /** 🔹 Trigger manual sync (Manager/ADMIN) */
    @PostMapping("/virtual-sync")
    public Map<String, Object> syncVirtualStock(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        inventoryService.syncVirtualStock(startDate, endDate);
        Map<String, Object> res = new HashMap<>();
        res.put("status", "ok");
        res.put("message", "Virtual storage synchronized successfully");
        return res;
    }

    @PostMapping("/auto-sync")
    public Map<String, Object> autoSync() {
        inventoryService.syncVirtualStock(LocalDate.now(), LocalDate.now().plusDays(30));
        Map<String, Object> res = new HashMap<>();
        res.put("status", "ok");
        res.put("message", "Auto virtual storage sync done");
        return res;
    }

}
