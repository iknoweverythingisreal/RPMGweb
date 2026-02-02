package com.rpmedia.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLightDTO {
    private Long id;
    private String displayName;
    private String calendarColor;
    private String role;
}
