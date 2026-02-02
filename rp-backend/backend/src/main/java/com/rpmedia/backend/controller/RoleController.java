package com.rpmedia.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminAccess() {
        return ResponseEntity.ok(Map.of("message", "Admin access granted."));
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<?> managerAccess() {
        return ResponseEntity.ok(Map.of("message", "Manager access granted."));
    }

    @GetMapping("/technical")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','TECHNICAL')")
    public ResponseEntity<?> technicalAccess() {
        return ResponseEntity.ok(Map.of("message", "Technical access granted."));
    }

    @GetMapping("/employee")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','TECHNICAL','EMPLOYEE')")
    public ResponseEntity<?> employeeAccess() {
        return ResponseEntity.ok(Map.of("message", "Employee access granted."));
    }

    @GetMapping("/public")
    public ResponseEntity<?> publicAccess() {
        return ResponseEntity.ok(Map.of("message", "Public endpoint (no auth required)."));
    }
}
