package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.UnitStatus;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.repository.ItemUnitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/repair")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RepairController {

    private final ItemRepository itemRepository;
    private final ItemUnitRepository itemUnitRepository;
    private final ObjectMapper objectMapper;

    /**
     * Mark non-serial items for repair by updating the 'spec' JSON field.
     */
    @PostMapping("/items/{itemId}/mark")
    @Transactional
    public ResponseEntity<?> markItemForRepair(
            @PathVariable("itemId") Long itemId,
            @RequestBody Map<String, Integer> payload) {

        Integer qty = payload.get("quantity");
        if (qty == null || qty <= 0)
            return ResponseEntity.badRequest().body("Quantity must be a positive number");

        return itemRepository.findById(itemId).map(item -> {
            ObjectNode spec = (item.getSpec() != null && item.getSpec().isObject())
                    ? (ObjectNode) item.getSpec()
                    : objectMapper.createObjectNode();

            int currentRepair = spec.has("repair_qty") ? spec.get("repair_qty").asInt() : 0;
            int totalStock = item.getTotalQuantity() != null ? item.getTotalQuantity() : 0;
            if (currentRepair + qty > totalStock) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Cannot report " + qty + " broken units: only "
                                + (totalStock - currentRepair) + " of " + totalStock + " units are not already in repair"));
            }
            spec.put("repair_qty", currentRepair + qty);

            item.setSpec(spec);
            itemRepository.save(item);
            return ResponseEntity
                    .ok(Map.of("message", "Item marked for repair", "current_repair_qty", currentRepair + qty));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Release non-serial items from repair.
     */
    @PostMapping("/items/{itemId}/release")
    @Transactional
    public ResponseEntity<?> releaseItemFromRepair(
            @PathVariable("itemId") Long itemId,
            @RequestBody Map<String, Integer> payload) {

        Integer qty = payload.get("quantity");
        if (qty == null)
            return ResponseEntity.badRequest().body("Quantity is required");

        return itemRepository.findById(itemId).map(item -> {
            if (item.getSpec() == null || !item.getSpec().has("repair_qty")) {
                return ResponseEntity.badRequest().body("No items in repair");
            }

            ObjectNode spec = (ObjectNode) item.getSpec();
            int currentRepair = spec.get("repair_qty").asInt();
            int newQty = Math.max(0, currentRepair - qty);
            spec.put("repair_qty", newQty);

            item.setSpec(spec);
            itemRepository.save(item);
            return ResponseEntity.ok(Map.of("message", "Items released from repair", "current_repair_qty", newQty));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Permanently write off broken units: removes them from repair queue AND
     * deducts them from total stock. Only units already reported broken
     * (repair_qty) can be written off.
     */
    @PostMapping("/items/{itemId}/writeoff")
    @Transactional
    public ResponseEntity<?> writeOffItem(
            @PathVariable("itemId") Long itemId,
            @RequestBody Map<String, Integer> payload) {

        Integer qty = payload.get("quantity");
        if (qty == null || qty <= 0)
            return ResponseEntity.badRequest().body("Quantity must be a positive number");

        return itemRepository.findById(itemId).map(item -> {
            if (item.getSpec() == null || !item.getSpec().has("repair_qty")) {
                return ResponseEntity.badRequest().body("No items in repair to write off");
            }

            ObjectNode spec = (ObjectNode) item.getSpec();
            int currentRepair = spec.get("repair_qty").asInt();
            if (qty > currentRepair) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Cannot write off " + qty + " units: only " + currentRepair + " are in repair"));
            }

            spec.put("repair_qty", currentRepair - qty);
            item.setSpec(spec);

            int total = item.getTotalQuantity() != null ? item.getTotalQuantity() : 0;
            item.setTotalQuantity(Math.max(0, total - qty));
            if (item.getAvailableQuantity() != null) {
                item.setAvailableQuantity(Math.max(0, item.getAvailableQuantity() - qty));
            }

            itemRepository.save(item);
            return ResponseEntity.ok(Map.of(
                    "message", "Wrote off " + qty + " units",
                    "current_repair_qty", currentRepair - qty,
                    "total_quantity", item.getTotalQuantity()));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark a serial unit (ItemUnit) as DAMAGED or UNDER_REPAIR.
     */
    @PutMapping("/units/{unitId}/status")
    @Transactional
    public ResponseEntity<?> updateUnitStatus(
            @PathVariable("unitId") Long unitId,
            @RequestParam("status") UnitStatus status) {

        return itemUnitRepository.findById(unitId).map(unit -> {
            unit.setStatus(status);
            unit.setUpdatedAt(Instant.now());
            itemUnitRepository.save(unit);
            return ResponseEntity.ok(Map.of("message", "Unit status updated to " + status));
        }).orElse(ResponseEntity.notFound().build());
    }
}
