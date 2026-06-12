package com.rpmedia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageInfoDTO {
    private String eventName;
    private Long eventId;
    private int quantity;
    private String startDate;
    private String endDate;
    private String status;
}
