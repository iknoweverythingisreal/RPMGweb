package com.rpmedia.backend.service;

import com.rpmedia.backend.model.*;
import com.rpmedia.backend.repository.EventItemRepository;
import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OverbookingService {

    @Autowired
    private EventRepository eventRepo;
    @Autowired
    private EventItemRepository eventItemRepo;
    @Autowired
    private UserRepository userRepo;
    @Autowired
    private EventHistoryService historyService;

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    /**
     * ============================
     * MANAGER: REQUEST OVERBOOK
     * ============================
     */
    @Transactional
    public EventItem requestOverbook(Long eventItemId, Long userId, String note) {

        EventItem ei = eventItemRepo.findById(eventItemId)
                .orElseThrow(() -> new RuntimeException("EventItem not found"));

        if (ei.getOverbookStatus() == OverbookStatus.APPROVED)
            throw new RuntimeException("Already approved");

        if (ei.getOverbookStatus() == OverbookStatus.PENDING)
            throw new RuntimeException("Already pending approval");

        ei.setOverbookStatus(OverbookStatus.PENDING);
        ei.setOverbookNote(note);
        int qty = Math.max(0, nvl(ei.getRequestedQuantity()) - nvl(ei.getAllocatedQuantity()));
        ei.setOverbookQty(qty);
        ei.setOverbookApprovedAt(null);
        ei.setOverbookApprovedBy(null);

        eventItemRepo.save(ei);

        // update event-level status
        Event ev = ei.getEvent();
        ev.setOverbookRequested(true);
        eventRepo.save(ev);

        historyService.log(
                ev.getId(),
                userId,
                "OVERBOOK_REQUEST",
                "Requested overbook for item " + ei.getItem().getName() + " note: " + note);

        return ei;
    }

    /**
     * ============================
     * CEO / APPROVER: APPROVE
     * ============================
     */
    @Transactional
    public EventItem approveOverbook(Long eventItemId, Long approverId, String note) {

        EventItem ei = eventItemRepo.findById(eventItemId)
                .orElseThrow(() -> new RuntimeException("EventItem not found"));

        if (ei.getOverbookStatus() != OverbookStatus.PENDING)
            throw new RuntimeException("Not pending approval");

        ei.setOverbookStatus(OverbookStatus.APPROVED);
        ei.setOverbookNote(note);
        ei.setOverbookApprovedBy(approverId);
        ei.setOverbookApprovedAt(LocalDateTime.now());

        // allocate full quantity
        ei.setAllocatedQuantity(ei.getRequestedQuantity());

        eventItemRepo.save(ei);

        // update event-level
        Event ev = ei.getEvent();
        ev.setOverbookApproved(true);
        ev.setOverbookApprovedAt(LocalDateTime.now());
        ev.setOverbookApprovedBy(approverId);
        ev.setOverbookNote(note);
        eventRepo.save(ev);

        historyService.log(
                ev.getId(),
                approverId,
                "OVERBOOK_APPROVED",
                "Overbook approved item " + ei.getItem().getName());

        return ei;
    }

    /**
     * ============================
     * CEO / APPROVER: REJECT
     * ============================
     */
    @Transactional
    public EventItem rejectOverbook(Long eventItemId, Long approverId, String reason) {

        EventItem ei = eventItemRepo.findById(eventItemId)
                .orElseThrow(() -> new RuntimeException("EventItem not found"));

        if (ei.getOverbookStatus() != OverbookStatus.PENDING)
            throw new RuntimeException("Not pending approval");

        ei.setOverbookStatus(OverbookStatus.REJECTED);
        ei.setOverbookNote(reason);
        ei.setOverbookApprovedBy(approverId);
        ei.setOverbookApprovedAt(LocalDateTime.now());

        // ไม่ allocate ให้
        eventItemRepo.save(ei);

        Event ev = ei.getEvent();
        ev.setOverbookApproved(false);
        ev.setOverbookNote(reason);

        eventRepo.save(ev);

        historyService.log(
                ev.getId(),
                approverId,
                "OVERBOOK_REJECTED",
                "Rejected overbook reason: " + reason);

        return ei;
    }

    /**
     * ============================
     * UTILS: ใช้ให้ Dashboard
     * ============================
     */
    public long countPending() {
        return eventItemRepo.countByOverbookStatus(OverbookStatus.PENDING);
    }

    public long countApproved() {
        return eventItemRepo.countByOverbookStatus(OverbookStatus.APPROVED);
    }

    public long countRejected() {
        return eventItemRepo.countByOverbookStatus(OverbookStatus.REJECTED);
    }
}
