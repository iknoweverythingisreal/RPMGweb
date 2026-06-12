package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.SerialActionRequest;
import com.rpmedia.backend.service.SerialOpsService;
import com.rpmedia.backend.dto.SerialUnitDTO;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class SerialOpsController {

        private final SerialOpsService serialOps;

        // ================================
        // 1) LINK / PICK SERIALS
        // ================================
        @PostMapping("/{eventId}/equipment/{eventItemId}/units")
        public ResponseEntity<?> linkUnits(
                        @PathVariable("eventId") Long eventId,
                        @PathVariable("eventItemId") Long eventItemId,
                        @RequestBody SerialActionRequest req) {
                int affected = serialOps.linkUnitsPicked(
                                eventId,
                                eventItemId,
                                req.getUnitIds(),
                                req.getNote());

                return ResponseEntity.ok(
                                Map.of(
                                                "eventId", eventId,
                                                "eventItemId", eventItemId,
                                                "linked", affected));
        }

        // ================================
        // 2) CHECKOUT (OUT)
        // ================================
        @PostMapping("/{eventId}/checkout")
        public ResponseEntity<?> checkout(
                        @PathVariable("eventId") Long eventId,
                        @RequestBody SerialActionRequest req) {
                int affected = serialOps.checkout(
                                eventId,
                                req.getEventItemId(),
                                req.getUnitIds(),
                                req.getUserId(),
                                req.getNote());

                return ResponseEntity.ok(
                                Map.of(
                                                "eventId", eventId,
                                                "checkedOut", affected));
        }

        // ================================
        // 3) RETURN
        // ================================
        @PostMapping("/{eventId}/return")
        public ResponseEntity<?> doReturn(
                        @PathVariable("eventId") Long eventId,
                        @RequestBody SerialActionRequest req) {
                int affected = serialOps.doReturn(
                                eventId,
                                req.getEventItemId(),
                                req.getUnitIds(),
                                req.getUserId(),
                                req.getNote());

                return ResponseEntity.ok(
                                Map.of(
                                                "eventId", eventId,
                                                "returned", affected));
        }

        // ================================
        // 4) DAMAGE
        // ================================
        @PostMapping("/{eventId}/damage")
        public ResponseEntity<?> markDamage(
                        @PathVariable("eventId") Long eventId,
                        @RequestBody SerialActionRequest req) {

                int affected = serialOps.markDamage(
                                eventId,
                                req.getUnitIds(),
                                req.getUserId(),
                                req.getNote());

                return ResponseEntity.ok(
                                Map.of(
                                                "eventId", eventId,
                                                "damaged", affected));
        }

        // ================================
        // 5) GET SERIALS FOR EVENT-ITEM
        // ================================
        @GetMapping("/{eventId}/event-items/{eventItemId}/serials")
        public ResponseEntity<?> getSerialsForEventItem(
                        @PathVariable("eventItemId") Long eventItemId) {
                List<SerialUnitDTO> list = serialOps.getUnitsByEventItem(eventItemId);

                return ResponseEntity.ok(list);
        }

}
