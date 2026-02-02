package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.Item;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.service.InventoryService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "*")
public class InventoryController {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EventItemRepository eventItemRepository; // ✅ ใช้ชื่อให้ตรงกับ service

    /**
     * Endpoint: /api/inventory/availability?startDate=2025-11-06&endDate=2025-11-08
     */
    @GetMapping("/availability")
    public List<Map<String, Object>> getAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Item> allItems = itemRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Item item : allItems) {
            // ✅ ใช้ repo ที่ชื่อถูกต้อง และฟังก์ชัน sumAllocatedOverlap (ที่มีอยู่แล้วใน
            // EventItemRepository)
            Long allocated = eventItemRepository.sumAllocatedOverlap(
                    item.getId(),
                    null, // excludeEventId = null
                    startDate,
                    endDate);

            BigDecimal allocatedVal = BigDecimal.valueOf(allocated != null ? allocated : 0);
            BigDecimal total = BigDecimal.valueOf(item.getTotalQuantity() != null ? item.getTotalQuantity() : 0);
            BigDecimal available = total.subtract(allocatedVal);

            Map<String, Object> row = new HashMap<>();
            row.put("itemId", item.getId());
            row.put("itemName", item.getName());
            row.put("category", item.getCategory());
            row.put("uom", item.getUom());
            row.put("totalQuantity", total);
            row.put("allocated", allocatedVal);
            row.put("available", available.max(BigDecimal.ZERO)); // กันค่าติดลบ

            result.add(row);
        }

        return result;
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
