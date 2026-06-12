package com.rpmedia.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssignedSerialDTO {
    private Long itemUnitId;
    private String serial;
    private String status;   // PICKED / OUT / RETURNED
}
