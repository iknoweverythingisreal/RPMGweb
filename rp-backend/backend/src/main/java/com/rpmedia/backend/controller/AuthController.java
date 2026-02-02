package com.rpmedia.backend.controller;

import com.rpmedia.backend.dto.LoginRequest;
import com.rpmedia.backend.dto.LoginResponse;
import com.rpmedia.backend.dto.RegisterRequest;
import com.rpmedia.backend.model.Role;
import com.rpmedia.backend.model.User;
import com.rpmedia.backend.repository.UserRepository;
import com.rpmedia.backend.util.JwtUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private JwtUtil jwtUtil;

        // ============================================
        // LOGIN
        // ============================================
        @PostMapping("/login")
        public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {

                User user = userRepository.findByEmail(request.getEmail()).orElse(null);

                if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("error", "Invalid email or password"));
                }

                // 🔒 Block REJECTED or Deactivated (unless PENDING)
                // Allow PENDING to login (restricted by UserStatusFilter)
                boolean isPending = "PENDING".equalsIgnoreCase(user.getStatus());
                if ("REJECTED".equalsIgnoreCase(user.getStatus()) || (!user.getIsActive() && !isPending)) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                        .body(Map.of("error",
                                                        "Your account is not active. Current status = "
                                                                        + user.getStatus()));
                }

                // 🔥 Create JWT with Phase 11 claims
                String token = jwtUtil.generateTokenWithClaims(
                                user.getEmail(),
                                Map.of(
                                                "userId", user.getId(),
                                                "role", user.getRole().name(),
                                                "status", user.getStatus(),
                                                "isActive", user.getIsActive()));

                LoginResponse response = new LoginResponse();
                response.setToken(token);
                response.setUserId(user.getId());
                response.setEmail(user.getEmail());
                response.setName(user.getName());
                response.setRole(user.getRole().name());
                response.setStatus(user.getStatus());
                response.setCalendarColor(user.getCalendarColor());

                return ResponseEntity.ok(response);
        }

        // ============================================
        // REGISTER
        // ============================================
        @PostMapping("/register")
        public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {

                if (userRepository.existsByEmail(request.getEmail())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(Map.of("error", "Email already exists"));
                }

                User user = new User();
                user.setEmail(request.getEmail());
                user.setUsername(request.getUsername() != null ? request.getUsername() : request.getEmail());
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                user.setName(request.getName());
                user.setCalendarColor(request.getCalendarColor() != null ? request.getCalendarColor() : "#3498db");

                // ⭐ NEW (Phase 11)
                user.setStatus("PENDING");
                user.setIsActive(false);
                user.setRole(Role.EMPLOYEE); // default role placeholder (will be overridden by Admin)

                userRepository.save(user);

                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(Map.of(
                                                "message", "Registration successful. Awaiting admin approval.",
                                                "status", "PENDING"));
        }

        // ============================================
        // VALIDATE TOKEN
        // ============================================
        @GetMapping("/validate")
        public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("valid", false));
                }

                String token = authHeader.substring(7);

                boolean isValid = jwtUtil.validateToken(token);

                if (!isValid)
                        return ResponseEntity.ok(Map.of("valid", false));

                return ResponseEntity.ok(Map.of(
                                "valid", true,
                                "email", jwtUtil.extractUsername(token),
                                "role", jwtUtil.extractRole(token)));
        }

        // ============================================
        // CURRENT USER INFO
        // ============================================
        @GetMapping("/me")
        public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {

                if (auth == null)
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(Map.of("error", "Unauthorized"));

                User user = userRepository.findByEmail(auth.getName()).orElse(null);

                if (user == null)
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                        .body(Map.of("error", "User not found"));

                return ResponseEntity.ok(Map.of(
                                "id", user.getId(),
                                "email", user.getEmail(),
                                "name", user.getName(),
                                "role", user.getRole().name(),
                                "status", user.getStatus(),
                                "isActive", user.getIsActive(),
                                "calendarColor", user.getCalendarColor()));
        }
}
