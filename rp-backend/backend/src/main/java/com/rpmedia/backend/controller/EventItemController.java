package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.BulkEventItemResponse;
import com.rpmedia.backend.dto.EventItemDTO;
import com.rpmedia.backend.dto.EventItemRequestDTO;
import com.rpmedia.backend.dto.SwapItemRequestDTO;
import com.rpmedia.backend.service.EventItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/event-items")
@CrossOrigin(origins = "*")
public class EventItemController {

    @Autowired
    private EventItemService eventItemService;

    // ==================== CRUD ====================

    // Get all
    @GetMapping
    public List<EventItemDTO> getAllEventItems() {
        return eventItemService.getAllEventItems();
    }

    // Get by ID
    @GetMapping("/{id}")
    public ResponseEntity<EventItemDTO> getById(@PathVariable("id") Long id) {
        return eventItemService.getEventItemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get by EventId
    @GetMapping("/event/{eventId}")
    public List<EventItemDTO> getByEventId(@PathVariable("eventId") Long eventId) {
        return eventItemService.getByEventId(eventId);
    }

    @GetMapping("/pending")
    public List<EventItemDTO> getPendingRentals() {
        return eventItemService.findPendingRentals().stream()
                .map(eventItemService::toDto)
                .toList();
    }

    @PostMapping("/bulk/{eventId}")
    public ResponseEntity<BulkEventItemResponse> addBulkItems(
            @PathVariable("eventId") Long eventId,
            @RequestBody List<EventItemRequestDTO> requests) {
        System.out.println("!!! [CONTROLLER] addBulkItems reached for eventId: " + eventId + ", items: "
                + (requests != null ? requests.size() : "null"));
        System.out.flush();
        return ResponseEntity.ok(eventItemService.addBulkItemsToEvent(eventId, requests));
    }

    @PostMapping
    public ResponseEntity<EventItemDTO> addSingleItem(@RequestBody EventItemRequestDTO request) {
        EventItemDTO created = eventItemService.addSingleItem(request);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<EventItemDTO> deleteEventItem(@PathVariable("id") Long id) {
        EventItemDTO deleted = eventItemService.delete(id);
        return ResponseEntity.ok(deleted);
    }

    // ==================== MVP: Update Quantity ====================

    @PutMapping("/{eventItemId}/quantity")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<String> updateQuantity(
            @PathVariable("eventItemId") Long eventItemId,
            @RequestBody java.util.Map<String, Object> payload) {

        Integer quantity = (Integer) payload.get("quantity");
        if (quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body("Quantity must be greater than 0");
        }

        eventItemService.updateQuantity(eventItemId, quantity);
        return ResponseEntity.ok("✅ Quantity updated successfully");
    }

    // ==================== Overbooking ====================

    @PostMapping("/{id}/approve-overbook")
    public ResponseEntity<EventItemDTO> approveOverbook(
            @PathVariable("id") Long id,
            @RequestParam("approverId") Long approverId,
            @RequestParam(value = "note", required = false) String note) {
        try {
            var entity = eventItemService.approveOverbook(id, approverId, note);
            return eventItemService.getEventItemById(entity.getId())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).build();
        }
    }

    @PostMapping("/{id}/reject-overbook")
    public ResponseEntity<EventItemDTO> rejectOverbook(
            @PathVariable("id") Long id,
            @RequestParam("approverId") Long approverId,
            @RequestParam(value = "note", required = false) String note) {
        try {
            var entity = eventItemService.rejectOverbook(id, approverId, note);
            return eventItemService.getEventItemById(entity.getId())
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).build();
        }
    }

    // ================= TECHNICAL WORKFLOW =================
    public ResponseEntity<String> prepareItems(
            @PathVariable("eventId") Long eventId,
            @RequestParam("preparedBy") Long preparedBy) {

        int count = eventItemService.markPrepared(eventId, preparedBy);
        return ResponseEntity.ok("✅ Marked " + count + " items as READY by user " + preparedBy);
    }

    /** 🔹 Technical - ตรวจของ (mark CHECKED) */
    @PutMapping("/{eventId}/check")
    public ResponseEntity<String> checkItems(
            @PathVariable("eventId") Long eventId,
            @RequestParam("checkedBy") Long checkedBy) {

        int count = eventItemService.markChecked(eventId, checkedBy);
        return ResponseEntity.ok("✅ Checked " + count + " items by user " + checkedBy);
    }

    /** 🔹 Technical - ขอเช่าเพิ่ม */
    @PostMapping("/{eventId}/request-rent")
    public ResponseEntity<String> requestRent(
            @PathVariable("eventId") Long eventId,
            @RequestParam("requesterId") Long requesterId,
            @RequestParam("itemId") Long itemId,
            @RequestParam("qty") Double qty,
            @RequestParam(value = "reason", required = false) String reason) {

        eventItemService.requestRentExternal(eventId, requesterId, itemId, qty, reason);
        return ResponseEntity.ok("📝 Rent request submitted for item " + itemId + " (" + qty + ")");
    }

    /** 🔹 Manager - อนุมัติหรือปฏิเสธการเช่า */
    @PutMapping("/approve-rent/{eventItemId}")
    public ResponseEntity<?> approveRent(
            @PathVariable("eventItemId") Long eventItemId,
            @RequestParam("approverId") Long approverId,
            @RequestParam("approved") boolean approved,
            @RequestParam(value = "note", required = false) String note) {
        try {
            eventItemService.approveRentExternal(eventItemId, approverId, approved, note);
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /** 🔹 Manager - Confirm ว่าของเลือกครบแล้ว */
    @PutMapping("/confirm/{eventId}")
    public ResponseEntity<String> confirmEventItems(
            @PathVariable("eventId") Long eventId,
            @RequestParam("confirmedBy") Long confirmedBy) {

        int count = eventItemService.confirmEventItems(eventId, confirmedBy);
        return ResponseEntity.ok("✅ Confirmed " + count + " items for event ID " + eventId);
    }

    // ✅ Reserve Item (Manager กดจอง)
    @PostMapping("/{eventId}/reserve")
    public ResponseEntity<EventItemDTO> reserveItem(
            @PathVariable("eventId") Long eventId,
            @RequestParam("itemId") Long itemId,
            @RequestParam("qty") int qty,
            @RequestParam("userId") Long userId) {

        var result = eventItemService.reserveItem(eventId, itemId, qty, userId);
        return ResponseEntity.ok(eventItemService.toDto(result));
    }

    // ✅ Confirm Reserved Items
    @PutMapping("/{eventId}/confirm-reserved")
    public ResponseEntity<String> confirmReserved(
            @PathVariable("eventId") Long eventId,
            @RequestParam("confirmedBy") Long confirmedBy) {

        eventItemService.confirmReservedItems(eventId, confirmedBy);
        return ResponseEntity.ok("✅ Reserved items confirmed for event " + eventId);
    }

    // ==================== RETURN FLOW ====================

    /** 🔹 TECH: Request Return */
    @PostMapping("/return-request/{eventId}")
    public ResponseEntity<String> requestReturn(
            @PathVariable("eventId") Long eventId,
            @RequestParam("requesterId") Long requesterId) {

        int count = eventItemService.requestReturn(eventId, requesterId);
        return ResponseEntity.ok("✅ Requested return for " + count + " items");
    }

    /** 🔹 ADMIN: Approve Return */
    @PutMapping("/return-approve/{eventId}")
    public ResponseEntity<String> approveReturn(
            @PathVariable("eventId") Long eventId,
            @RequestParam("approverId") Long approverId) {

        int count = eventItemService.approveReturn(eventId, approverId);
        return ResponseEntity.ok("✅ Approved return for " + count + " items. Inventory released.");
    }

    // ==================== ROOM ASSIGNMENT ====================

    @PostMapping("/{id}/assign-room")
    public ResponseEntity<String> assignToRoom(
            @PathVariable("id") Long id,
            @RequestParam("roomName") String roomName,
            @RequestParam("quantity") Integer quantity) {
        try {
            eventItemService.assignToRoom(id, roomName, quantity);
            return ResponseEntity.ok("✅ Item assigned to room: " + roomName);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ " + e.getMessage());
        }
    }

    @DeleteMapping("/event/{eventId}/room")
    public ResponseEntity<String> deleteRoom(
            @PathVariable("eventId") Long eventId,
            @RequestParam("roomName") String roomName) {
        try {
            eventItemService.deleteRoomFromEvent(eventId, roomName);
            return ResponseEntity.ok("✅ Room deleted: " + roomName);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("❌ " + e.getMessage());
        }
    }

    @PutMapping("/{id}/remark")
    public ResponseEntity<String> updateRemark(
            @PathVariable("id") Long id,
            @RequestBody java.util.Map<String, String> payload) {
        String remark = payload.get("remark");
        String customName = payload.get("customName");
        String customDescription = payload.get("customDescription");

        eventItemService.updateItemDetails(id, remark, customName, customDescription);
        return ResponseEntity.ok("✅ Item details updated");
    }

}
