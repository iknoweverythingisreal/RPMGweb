package com.rpmedia.backend.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateEventRequest {
  private String title;
  private String description;
  private String location;
  private LocalDate startDate;
  private LocalDate endDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private Long ownerId;                 // = created_by
  private String type;                  // เก็บใน custom_fields.type ตอนนี้
  private Map<String,Object> customFields;
}
