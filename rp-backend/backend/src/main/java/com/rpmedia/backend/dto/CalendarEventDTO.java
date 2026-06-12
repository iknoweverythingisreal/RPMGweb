package com.rpmedia.backend.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarEventDTO {
  private Long id;
  private String title;
  private String description;
  private LocalDate startDate;
  private LocalDate endDate;
  private LocalTime startTime;
  private LocalTime endTime;
  private Long ownerId; // = events.created_by (User) OR events.calendar_owner_id (CalendarOwner)
  private String ownerName; // join users.name OR calendar_owners.name
  private String ownerColorHex; // join users.calendar_color OR calendar_owners.color_hex
  private String ownerRole; // join users.role (for role-based UI features)
  private List<String> allColors; // All participant colors (owner + managers) for gradient
  private String type; // จาก custom_fields.type
  private String location;
  private List<Long> teamupSubcalendarIds; // All sub-calendar IDs
  private List<Long> managerIds;
  private Long techLeadId;
}
