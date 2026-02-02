package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.EventHistory;
import com.rpmedia.backend.service.EventHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/event-history")
@CrossOrigin(origins = "*")
public class EventHistoryController {

    @Autowired
    private EventHistoryService service;

    @GetMapping("/event/{eventId}")
    public List<EventHistory> getHistory(@PathVariable("eventId") Long eventId) {
        return service.getHistoryByEvent(eventId);
    }

    @PostMapping
    public EventHistory createHistory(@RequestBody Map<String, Object> request) {
        Long eventId = Long.valueOf(request.get("eventId").toString());
        String changeType = request.get("changeType").toString();
        Map<String, Object> data = (Map<String, Object>) request.get("data");
        return service.logChange(eventId, changeType, data);
    }
}
