package com.rpmedia.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkAvailabilityRequestDTO {
    private List<Long> itemIds;
    private Long eventId;
    private String startDate;
    private String endDate;
}
