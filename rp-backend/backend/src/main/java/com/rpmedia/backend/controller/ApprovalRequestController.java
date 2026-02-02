package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.ApprovalRequest;
import com.rpmedia.backend.service.ApprovalRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalRequestController {

    private final ApprovalRequestService service;

    @PostMapping("/request")
    @PreAuthorize("hasAnyRole('MANAGER','TECH_LEAD','ADMIN')")
    public ApprovalRequest create(@RequestBody ApprovalRequest req) {
        return service.create(req);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('MANAGER','TECH_LEAD','ADMIN')")
    public List<ApprovalRequest> getPending() {
        return service.getPending();
    }

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('MANAGER','TECH_LEAD','ADMIN')")
    public List<ApprovalRequest> getByEvent(@PathVariable("eventId") Long eventId) {
        return service.getByEvent(eventId);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('TECH_LEAD','ADMIN')")
    public ApprovalRequest approve(
            @PathVariable("id") Long id,
            @RequestParam("approverId") Long approverId) {
        return service.approve(id, approverId);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('TECH_LEAD','ADMIN')")
    public ApprovalRequest reject(
            @PathVariable("id") Long id,
            @RequestParam("approverId") Long approverId,
            @RequestParam(value = "note", required = false) String note) {
        return service.reject(id, approverId, note);
    }
}
