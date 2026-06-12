package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.ItemShortageDTO;
import com.rpmedia.backend.service.ShortageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shortages")
@CrossOrigin(origins = "*") // อนุญาตให้ frontend เชื่อมได้
public class ShortageController {

    @Autowired
    private ShortageService shortageService;

    @GetMapping("/event/{eventId}")
    @PreAuthorize("hasAnyRole('MANAGER','TECH_LEAD','ADMIN')")
    public List<ItemShortageDTO> getShortages(@PathVariable("eventId") Long eventId) {
        return shortageService.getShortagesForEvent(eventId);
    }

}
