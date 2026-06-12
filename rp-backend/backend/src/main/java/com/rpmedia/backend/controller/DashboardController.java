package com.rpmedia.backend.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.HashMap;

import com.rpmedia.backend.repository.EventRepository;
import com.rpmedia.backend.model.ItemStatus;
import com.rpmedia.backend.repository.EventItemRepository;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private EventRepository eventRepo;
    @Autowired
    private EventItemRepository itemRepo;

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        long totalEvents = eventRepo.count();

        long draft = itemRepo.countByStatus(ItemStatus.DRAFT);
        long confirmed = itemRepo.countByStatus(ItemStatus.CONFIRMED);
        long ready = itemRepo.countByStatus(ItemStatus.READY);
        long checked = itemRepo.countByStatus(ItemStatus.CHECKED);
        long rentPending = itemRepo.countByStatus(ItemStatus.PENDING_RENT);

        summary.put("totalEvents", totalEvents);
        summary.put("draftItems", draft);
        summary.put("confirmedItems", confirmed);
        summary.put("readyItems", ready);
        summary.put("checkedItems", checked);
        summary.put("rentPending", rentPending);

        return summary;
    }
}
