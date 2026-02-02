package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.EventItem;
import com.rpmedia.backend.model.OverbookStatus;
import com.rpmedia.backend.service.OverbookingService;
import com.rpmedia.backend.service.EventItemService;
import com.rpmedia.backend.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/overbooking")
@CrossOrigin(origins = "*")
public class OverbookingController {

    @Autowired
    private OverbookingService overbookingService;

    @Autowired
    private EventItemService eventItemService;

    /**
     * ============================
     * MANAGER: REQUEST OVERBOOK
     * ============================
     */
    @PostMapping("/request/{eventItemId}")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ResponseEntity<?> request(
            @PathVariable Long eventItemId,
            @RequestParam Long userId,
            @RequestBody Map<String, String> body) {

        String note = body.getOrDefault("note", "");

        EventItem updated = overbookingService.requestOverbook(eventItemId, userId, note);
        return ResponseEntity.ok(Map.of(
                "message", "Overbook requested",
                "eventItemId", eventItemId,
                "status", updated.getOverbookStatus()
        ));
    }

    /**
     * ============================
     * ADMIN/CEO: APPROVE
     * ============================
     */
    @PostMapping("/approve/{eventItemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approve(
            @PathVariable Long eventItemId,
            @RequestParam Long approverId,
            @RequestBody Map<String, String> body) {

        String note = body.getOrDefault("note", "");
        EventItem updated = overbookingService.approveOverbook(eventItemId, approverId, note);

        return ResponseEntity.ok(Map.of(
                "message", "Overbook approved",
                "eventItemId", eventItemId,
                "status", updated.getOverbookStatus()
        ));
    }

    /**
     * ============================
     * ADMIN/CEO: REJECT
     * ============================
     */
    @PostMapping("/reject/{eventItemId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reject(
            @PathVariable Long eventItemId,
            @RequestParam Long approverId,
            @RequestBody Map<String, String> body) {

        String reason = body.getOrDefault("reason", "");
        EventItem updated = overbookingService.rejectOverbook(eventItemId, approverId, reason);

        return ResponseEntity.ok(Map.of(
                "message", "Overbook rejected",
                "eventItemId", eventItemId,
                "status", updated.getOverbookStatus()
        ));
    }

    /**
     * ============================
     * DASHBOARD SUMMARY
     * ============================
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> summary() {

        long pending = overbookingService.countPending();
        long approved = overbookingService.countApproved();
        long rejected = overbookingService.countRejected();

        return ResponseEntity.ok(Map.of(
                "pending", pending,
                "approved", approved,
                "rejected", rejected
        ));
    }

    /**
     * ============================
     * LIST PENDING ITEMS
     * ============================
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listPending() {

        List<EventItem> list = eventItemService.findByOverbookStatus(OverbookStatus.PENDING);

        return ResponseEntity.ok(list.stream().map(ei -> Map.of(
                "eventItemId", ei.getId(),
                "itemName", ei.getItem().getName(),
                "requestedQty", ei.getRequestedQuantity(),
                "allocatedQty", ei.getAllocatedQuantity(),
                "overbookQty", ei.getOverbookQty(),
                "eventId", ei.getEvent().getId(),
                "eventTitle", ei.getEvent().getTitle()
        )));
    }
}
