package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.EquipmentAvailabilityRow; // projection interface
import com.rpmedia.backend.repository.EquipmentQueryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class EquipmentBrowseController {

    private final EquipmentQueryRepository equipmentQueryRepository;

    public EquipmentBrowseController(EquipmentQueryRepository equipmentQueryRepository) {
        this.equipmentQueryRepository = equipmentQueryRepository;
    }

    @GetMapping("/equipment")
    public Map<String, Object> getAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // กันค่าผิดปกติ
        page = Math.max(page, 0);
        size = Math.max(size, 1);

        Pageable pageable = PageRequest.of(page, size);
        Page<EquipmentAvailabilityRow> p =
                equipmentQueryRepository.findAvailability(startDate, endDate, category, q, pageable);

        // คืน format ที่ FE ใช้ง่าย
        return Map.of(
                "content", p.getContent(),           // list ของแถว (projection interface)
                "page", p.getNumber(),
                "size", p.getSize(),
                "totalElements", p.getTotalElements(),
                "totalPages", p.getTotalPages()
        );
    }
}
