package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventActionType;
import com.rpmedia.backend.model.EventStatus;
import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.service.EventHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
@CrossOrigin(origins = "*")
@PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
public class ManagerController {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventHistoryService eventHistoryService;

    // ✅ 1. Manager สร้าง Event ใหม่
    @PostMapping("/create-event")
    public ResponseEntity<?> createEvent(@RequestBody Event event) {
        event.setStatus(EventStatus.DRAFT); // default state
        Event saved = eventRepository.save(event);

        // บันทึก history
        eventHistoryService.logChange(saved.getId(), EventActionType.CREATE_EVENT.name(), Map.of(
                "title", saved.getTitle(),
                "status", "Created by Manager",
                "createdAt", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Event created successfully",
                "eventId", saved.getId()));
    }

    // ✅ 2. อนุมัติการ Overbook
    @PostMapping("/approve-overbook/{eventId}")
    public ResponseEntity<?> approveOverbook(@PathVariable("eventId") Long eventId,
            @RequestBody(required = false) Map<String, Object> payload) {
        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event not found"));
        }

        Event ev = eventOpt.get();
        ev.setStatus(EventStatus.OVERBOOK_APPROVED);
        eventRepository.save(ev);

        String note = payload != null && payload.containsKey("note") ? payload.get("note").toString() : "No remarks";

        // log การอนุมัติ
        eventHistoryService.logChange(eventId, EventActionType.APPROVE_OVERBOOK.name(), Map.of(
                "status", "Overbook approved by Manager",
                "note", note,
                "timestamp", LocalDateTime.now().toString()));

        return ResponseEntity.ok(Map.of(
                "message", "Overbooking approved successfully",
                "eventId", eventId,
                "status", ev.getStatus().name()));
    }
}
