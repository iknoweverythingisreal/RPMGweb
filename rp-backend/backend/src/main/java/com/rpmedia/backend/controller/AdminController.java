package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.Role;
import com.rpmedia.backend.model.User;
import com.rpmedia.backend.model.Event;
import com.rpmedia.backend.model.EventStatus;
import com.rpmedia.backend.model.EventHistory;

import com.rpmedia.backend.repository.UserRepository;
import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.repository.EventHistoryRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventHistoryRepository eventHistoryRepository;

    // ✅ 1. ดึงรายการผู้ใช้ทั้งหมด
    @GetMapping("/users")
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // ✅ 2. เปลี่ยน role ผู้ใช้ (ใช้ในระบบเก่าได้)
    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable("id") Long id, @RequestBody Map<String, String> request) {
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        try {
            Role newRole = Role.valueOf(request.get("role").toUpperCase());
            user.setRole(newRole);
            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Role updated successfully",
                    "userId", id,
                    "newRole", newRole));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid role. Must be one of: " + Arrays.toString(Role.values())));
        }
    }

    // ✅ 3. ปิด/เปิดการใช้งาน user (soft toggle)
    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable("id") Long id, @RequestBody Map<String, Boolean> request) {
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        Boolean active = request.getOrDefault("isActive", true);
        user.setIsActive(active);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User status updated",
                "userId", id,
                "active", active));
    }

    // ✅ 4. ลบผู้ใช้ (Soft Delete)
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        user.setIsActive(false);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User deactivated",
                "userId", id));
    }

    // ============================================
    // ⛳ NEW PHASE 11 FUNCTIONS
    // ============================================

    // 🎯 5. ดึง "ผู้ใช้ที่รออนุมัติ" ตาม Phase 11 (ใช้ status ไม่ใช่ role)
    @GetMapping("/users/pending")
    public ResponseEntity<?> getPendingUsers() {
        var pendingUsers = userRepository.findByStatus("PENDING");
        return ResponseEntity.ok(Map.of("pendingUsers", pendingUsers));
    }

    // 🎯 6. อนุมัติผู้ใช้
    @PutMapping("/users/{id}/approve")
    public ResponseEntity<?> approveUser(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();

        try {
            Role newRole = Role.valueOf(body.getOrDefault("role", "EMPLOYEE").toUpperCase());

            user.setRole(newRole);
            user.setStatus("ACTIVE");
            user.setIsActive(true);

            userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "message", "User approved successfully",
                    "userId", id,
                    "assignedRole", newRole.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid role. Valid: " + Arrays.toString(Role.values())));
        }
    }

    // 🎯 7. ปฏิเสธ user (REJECTED)
    @PutMapping("/users/{id}/reject")
    public ResponseEntity<?> rejectUser(@PathVariable("id") Long id) {
        var userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();

        user.setStatus("REJECTED");
        user.setIsActive(false);

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "message", "User rejected successfully",
                "userId", id));
    }

    // ============================================
    // เหมือนของเดิม (ไม่แก้)
    // ============================================

    @GetMapping("/events")
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    @GetMapping("/events/{eventId}/history")
    public List<EventHistory> getEventHistory(@PathVariable("eventId") Long eventId) {
        return eventHistoryRepository.findByEventId(eventId);
    }

    @PutMapping("/events/{eventId}/status")
    public ResponseEntity<?> updateEventStatus(
            @PathVariable("eventId") Long eventId,
            @RequestBody Map<String, String> request) {
        var eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Event not found"));
        }

        var event = eventOpt.get();
        String newStatus = request.get("status");

        try {
            EventStatus statusEnum = EventStatus.valueOf(newStatus.toUpperCase());
            event.setStatus(statusEnum);
            eventRepository.save(event);

            return ResponseEntity.ok(Map.of(
                    "message", "Event status updated",
                    "eventId", eventId,
                    "newStatus", statusEnum.name()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid status. Valid: " + Arrays.toString(EventStatus.values())));
        }
    }

    @GetMapping("/event-statuses")
    public ResponseEntity<?> getAllEventStatuses() {
        return ResponseEntity.ok(Map.of(
                "availableStatuses",
                Arrays.stream(EventStatus.values()).map(Enum::name).toList()));
    }
}
