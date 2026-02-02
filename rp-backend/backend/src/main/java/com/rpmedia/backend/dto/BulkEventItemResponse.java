package com.rpmedia.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkEventItemResponse {
    private Long eventId;
    private List<EventItemDTO> items;
}
