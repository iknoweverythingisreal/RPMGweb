package com.rpmedia.backend.dto;
import lombok.Data;

@Data
public class UserListDTO {
    private Long id;
    private String email;
    private String fullName;
    private String role;
    private String status;
    private Long teamId;
    private Long departmentId;
    private String position;
    private String createdAt;
}
