package com.rpmedia.backend.service;

import com.rpmedia.backend.dto.UserListDTO;
import com.rpmedia.backend.model.Role;
import com.rpmedia.backend.model.User;
import com.rpmedia.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    public List<UserListDTO> getPendingUsers() {
        return userRepository.findByStatus("PENDING")
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public void approveUser(Long userId, String roleStr) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // convert string -> enum
        Role selectedRole = Role.valueOf(roleStr);

        user.setStatus("ACTIVE");
        user.setIsActive(true);
        user.setRole(selectedRole);

        userRepository.save(user);
    }

    public void rejectUser(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus("REJECTED");
        user.setIsActive(false);

        userRepository.save(user);
    }

    private UserListDTO toDTO(User user) {
        UserListDTO dto = new UserListDTO();

        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getName());              // <-- ใช้ getName() ตาม entity
        dto.setRole(user.getRole().name());           // enum → string
        dto.setStatus(user.getStatus());              // ต้องมี field status ใน entity
        dto.setDepartmentId(user.getDepartment() != null 
                ? user.getDepartment().getId() 
                : null);
        dto.setTeamId(null);                          // User ไม่มี teamId → set null
        dto.setPosition(null);                        // User ไม่มี position → set null
        dto.setCreatedAt(user.getCreatedAt() != null
                ? user.getCreatedAt().toString()
                : null);

        return dto;
    }
}
