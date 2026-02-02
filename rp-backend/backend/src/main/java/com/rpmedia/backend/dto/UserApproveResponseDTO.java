package com.rpmedia.backend.dto;

import lombok.Data;

@Data
public class UserApproveResponseDTO {
    private Long id;
    private String email;
    private String status;
    private String role;
}
