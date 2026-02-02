package com.rpmedia.backend.controller;

import com.rpmedia.backend.service.EventLifecycleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/lifecycle")
@CrossOrigin(origins = "*")
public class EventLifecycleController {

    @Autowired
    private EventLifecycleService lifecycleService;

    @PostMapping("/update")
    public Map<String, Object> manualUpdate() {
        lifecycleService.updateEventStatuses();
        Map<String, Object> res = new HashMap<>();
        res.put("status", "ok");
        res.put("message", "Lifecycle update triggered manually");
        return res;
    }
}
