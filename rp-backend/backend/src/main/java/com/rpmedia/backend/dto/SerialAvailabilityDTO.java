package com.rpmedia.backend.dto;

import lombok.Data;

@Data
public class SerialAvailabilityDTO {
    private Long itemUnitId;   // คือ physical device ID
    private String serial;     // หมายเลข serial ตัวจริง
    private String status;     // AVAILABLE, IN_USE, UNAVAILABLE
}
