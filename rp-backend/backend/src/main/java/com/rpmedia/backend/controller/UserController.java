package com.rpmedia.backend.controller;

import com.rpmedia.backend.model.User;
import com.rpmedia.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    // Get all users
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // Get user by ID
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userRepository.findById(id).orElse(null);
    }

    // Create user
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }

    // Update user
    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User updatedUser) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null) {
            user.setName(updatedUser.getName());
            user.setEmail(updatedUser.getEmail());
            user.setDepartment(updatedUser.getDepartment());
            return userRepository.save(user);
        }
        return null;
    }

    // Delete user
    @DeleteMapping("/{id}")
    public void deleteUser(@PathVariable Long id) {
        userRepository.deleteById(id);
    }

    @GetMapping("/light")
    public List<com.rpmedia.backend.dto.UserLightDTO> getUsersLight() {
        return userRepository.findAll().stream()
                // is_active เป็น NULL สำหรับ user เก่าที่ถูกสร้างก่อนมีคอลัมน์นี้ -> ถือว่ายัง active อยู่
                .filter(u -> u.getIsActive() == null || u.getIsActive())
                .map(u -> new com.rpmedia.backend.dto.UserLightDTO(
                        u.getId(),
                        // ใช้ name (ที่แมปกับ full_name) ถ้าไม่มี fallback เป็น email
                        (u.getName() != null && !u.getName().trim().isEmpty()) ? u.getName() : u.getEmail(),
                        (u.getCalendarColor() != null && !u.getCalendarColor().trim().isEmpty())
                                ? u.getCalendarColor()
                                : "#888888",
                        u.getRole() != null ? u.getRole().name() : null))
                .collect(java.util.stream.Collectors.toList());
    }

    @GetMapping("/me")
    public User getCurrentUser(@RequestParam(required = false) Long id) {
        // Optional: allow fetching user by ID parameter
        if (id != null) {
            return userRepository.findById(id).orElse(null);
        }
        return null;
    }

}
