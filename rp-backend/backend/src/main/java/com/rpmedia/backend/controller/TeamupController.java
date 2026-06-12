package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.CalendarOwner;
import com.rpmedia.backend.repository.CalendarOwnerRepository;
import com.rpmedia.backend.service.IntegrationStateService;
import com.rpmedia.backend.service.TeamupIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/teamup")
@RequiredArgsConstructor
public class TeamupController {

    private final TeamupIntegrationService teamupService;
    private final IntegrationStateService integrationStateService;
    private final CalendarOwnerRepository calendarOwnerRepository;

    /** 🔹 ดึงข้อมูลทั้งหมดจาก Teamup (default) */
    @GetMapping("/sync")
    public ResponseEntity<String> syncFromTeamup() {
        int count = teamupService.syncFromTeamup();
        return ResponseEntity.ok("Synced " + count + " events (default range).");
    }

    /** 🔹 ดึงข้อมูลตามช่วงเวลา */
    @GetMapping("/sync-range")
    public ResponseEntity<String> syncByRange(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDate fromDate = (from != null) ? LocalDate.parse(from) : LocalDate.now().minusMonths(1);
        LocalDate toDate = (to != null) ? LocalDate.parse(to) : LocalDate.now().plusMonths(1);

        int count = teamupService.syncFromTeamupRange(fromDate, toDate);
        integrationStateService.updateSyncTime("last_teamup_sync", LocalDateTime.now());
        return ResponseEntity.ok("Synced " + count + " events from " + fromDate + " to " + toDate);
    }

    /** 🔹 ส่ง Event ที่สร้างในระบบเรา → Teamup */
    @PostMapping("/push/{eventId}")
    public ResponseEntity<String> pushEvent(@PathVariable Long eventId) {
        teamupService.pushEventToTeamup(eventId);
        return ResponseEntity.ok("Pushed event ID " + eventId + " to Teamup.");
    }

    @GetMapping("/sync-changes")
    public ResponseEntity<String> syncChangedEvents() {
        LocalDateTime lastSync = integrationStateService.getLastSync("last_teamup_sync");
        int count = teamupService.syncChangesSince(lastSync);
        integrationStateService.updateSyncTime("last_teamup_sync", LocalDateTime.now());
        return ResponseEntity.ok("Synced " + count + " changed events since " + lastSync);
    }

    @GetMapping("/backfill-all")
    public ResponseEntity<String> backfillAll() {
        int total = teamupService.backfillAllYears();
        return ResponseEntity.ok("Backfilled total " + total + " events (last 3 years).");
    }

    /** 🔹 ดึงข้อมูลจากวันนี้ไปอีก 2 ปี (ADMIN ONLY) */
    @GetMapping("/sync-future")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncFuture() {
        // Sync owners first as requested
        int ownerCount = teamupService.syncOwnersFromTeamup();

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusYears(2);
        int eventCount = teamupService.syncFromTeamupRange(from, to);

        integrationStateService.updateSyncTime("last_teamup_sync_future", LocalDateTime.now());
        return ResponseEntity
                .ok("Synced " + ownerCount + " owners and " + eventCount + " events from " + from + " to " + to);
    }

    /** 🔹 Sync Owners (ADMIN ONLY) */
    @GetMapping("/sync-owners")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncOwners() {
        int count = teamupService.syncOwnersFromTeamup();
        return ResponseEntity.ok("Synced " + count + " owners from Teamup.");
    }

    /** 🔹 Get Owners List (Public) */
    @GetMapping("/owners")
    public ResponseEntity<List<CalendarOwner>> getOwners() {
        return ResponseEntity.ok(calendarOwnerRepository.findAll());
    }

}
