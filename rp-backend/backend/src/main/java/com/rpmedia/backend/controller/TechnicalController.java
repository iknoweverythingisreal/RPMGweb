package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.*;
import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.ItemRepository;
import com.rpmedia.backend.service.EventHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/technical")
@CrossOrigin(origins = "*")
public class TechnicalController {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventItemRepository eventItemRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private EventHistoryService eventHistoryService;

    /**
     * ✅ 1. Add item to event (ใช้ ItemStatus enum)
     */
    @PostMapping("/event/{eventId}/add-item")
    @PreAuthorize("hasAnyRole('TECHNICAL','TECH_LEAD','ADMIN')")
    public ResponseEntity<?> addItemToEvent(@PathVariable Long eventId, @RequestBody Map<String, Object> body) {
        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event not found"));
        }

        Long itemId = Long.valueOf(body.get("itemId").toString());
        var itemOpt = itemRepository.findById(itemId);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Item not found"));
        }

        Integer qty = Integer.valueOf(body.getOrDefault("quantity", 1).toString());
        String remark = body.getOrDefault("remark", "").toString();
        String rateType = body.getOrDefault("rateType", "PER_DAY").toString();

        EventItem ei = new EventItem();
        ei.setEvent(eventOpt.get());
        ei.setItem(itemOpt.get());
        ei.setRequestedQuantity(qty);
        ei.setAllocatedQuantity(0);
        ei.setRateType(rateType);
        ei.setRemark(remark);
        ei.setStatus(ItemStatus.ALLOCATED); // ⭐ ใช้ enum
        ei.setCreatedAt(LocalDateTime.now());

        eventItemRepository.save(ei);

        eventHistoryService.logChange(eventId, EventActionType.ADD_EQUIPMENT.name(), Map.of(
                "itemId", itemId,
                "requestedQuantity", qty,
                "remark", remark,
                "timestamp", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Item added successfully",
                "eventId", eventId,
                "itemId", itemId));
    }

    /**
     * ✅ 2. Edit item in event — (ไม่มีการแก้ status โดยตรง)
     */
    @PutMapping("/event/{eventId}/item/{eventItemId}")
    @PreAuthorize("hasAnyRole('TECHNICAL','TECH_LEAD','ADMIN')")
    public ResponseEntity<?> editEventItem(
            @PathVariable Long eventId,
            @PathVariable Long eventItemId,
            @RequestBody Map<String, Object> body) {

        var eiOpt = eventItemRepository.findById(eventItemId);
        if (eiOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event item not found"));
        }

        EventItem ei = eiOpt.get();

        if (body.containsKey("requestedQuantity"))
            ei.setRequestedQuantity(Integer.valueOf(body.get("requestedQuantity").toString()));

        if (body.containsKey("allocatedQuantity"))
            ei.setAllocatedQuantity(Integer.valueOf(body.get("allocatedQuantity").toString()));

        if (body.containsKey("remark"))
            ei.setRemark(body.get("remark").toString());

        if (body.containsKey("rateType"))
            ei.setRateType(body.get("rateType").toString());

        // ❌ ไม่แก้ status ใน Controller — เพื่อป้องกันการ override ผิด
        // ถ้าต้องแก้สถานะ ให้ทำผ่าน Service ที่มี workflow เท่านั้น

        ei.setUpdatedAt(LocalDateTime.now());
        eventItemRepository.save(ei);

        eventHistoryService.logChange(eventId, EventActionType.EDIT_EQUIPMENT.name(), Map.of(
                "eventItemId", eventItemId,
                "updatedFields", body,
                "timestamp", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Event item updated successfully",
                "eventItemId", eventItemId));
    }

    /**
     * ✅ 3. Recheck before event — ไม่ยุ่ง item status
     */
    @PostMapping("/recheck/{eventId}")
    @PreAuthorize("hasAnyRole('TECHNICAL','TECH_LEAD','ADMIN')")
    public ResponseEntity<?> recheckItems(@PathVariable Long eventId) {
        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event not found"));
        }

        Event event = eventOpt.get();
        event.setStatus(EventStatus.IN_PROGRESS);
        eventRepository.save(event);

        eventHistoryService.logChange(eventId, EventActionType.RECHECK_ITEM.name(), Map.of(
                "message", "Technical team rechecked items",
                "newStatus", event.getStatus().name(),
                "timestamp", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Recheck completed",
                "eventId", eventId,
                "status", event.getStatus().name()));
    }

    /**
     * ✅ 4. Return equipment
     */
    @PostMapping("/return/{eventId}")
    @PreAuthorize("hasAnyRole('TECHNICAL','TECH_LEAD','ADMIN')")
    public ResponseEntity<?> returnItems(@PathVariable Long eventId) {
        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event not found"));
        }

        Event event = eventOpt.get();
        event.setStatus(EventStatus.COMPLETED);
        eventRepository.save(event);

        eventHistoryService.logChange(eventId, EventActionType.RETURN_ITEM.name(), Map.of(
                "message", "Equipment returned to storage",
                "newStatus", event.getStatus().name(),
                "timestamp", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Items returned successfully",
                "eventId", eventId,
                "status", event.getStatus().name()));
    }

    /**
     * ✅ 5. Report damage
     */
    @PostMapping("/report-damage/{eventId}")
    @PreAuthorize("hasAnyRole('TECHNICAL','TECH_LEAD','ADMIN')")
    public ResponseEntity<?> reportDamage(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> report) {

        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event not found"));
        }

        Event event = eventOpt.get();
        event.setStatus(EventStatus.ISSUE_REPORTED);
        eventRepository.save(event);

        eventHistoryService.logChange(eventId, EventActionType.REPORT_DAMAGE.name(), Map.of(
                "details", report,
                "newStatus", event.getStatus().name(),
                "timestamp", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Damage report submitted",
                "eventId", eventId,
                "status", event.getStatus().name()));
    }
}
