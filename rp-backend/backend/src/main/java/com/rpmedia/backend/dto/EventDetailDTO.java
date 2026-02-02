package com.rpmedia.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class EventDetailDTO {
    private Long id;                  // events.id
    private String title;             // events.title
    private String description;       // events.description
    private String location;          // events.location

    private LocalDate startDate;      // events.start_date
    private LocalDate endDate;        // events.end_date
    private LocalTime startTime;      // events.start_time
    private LocalTime endTime;        // events.end_time

    private List<EventItemDTO> items; // join event_items
}
