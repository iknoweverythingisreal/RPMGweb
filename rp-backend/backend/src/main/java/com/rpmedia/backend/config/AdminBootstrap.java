package com.rpmedia.backend.config;

import com.rpmedia.backend.model.Role;
import com.rpmedia.backend.model.User;
import com.rpmedia.backend.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * ตัวช่วย bootstrap admin คนแรก / reset รหัส admin โดยใช้ BCrypt encoder ของแอปเอง
 * เปิดทำงานเฉพาะเมื่อมี env ทั้งสองตัว:
 *   ADMIN_BOOTSTRAP_EMAIL
 *   ADMIN_BOOTSTRAP_PASSWORD
 * ถ้ามี user email นั้นอยู่แล้ว -> reset รหัส + ตั้งเป็น ADMIN/ACTIVE
 * ถ้ายังไม่มี -> สร้างใหม่เป็น ADMIN/ACTIVE
 * ใช้เสร็จแล้วให้ลบ env สองตัวนี้ออกจาก compose (โค้ดจะเงียบเองเมื่อไม่มี env)
 */
@Component
@Order(100) // ให้รันหลัง fixData / seed อื่นๆ
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_BOOTSTRAP_EMAIL:}")
    private String bootstrapEmail;

    @Value("${ADMIN_BOOTSTRAP_PASSWORD:}")
    private String bootstrapPassword;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return; // ไม่ได้ตั้ง env -> ไม่ทำอะไร
        }

        User user = userRepository.findByEmail(bootstrapEmail).orElseGet(User::new);
        boolean isNew = user.getId() == null;

        user.setEmail(bootstrapEmail);
        if (isNew) {
            user.setUsername(bootstrapEmail);
            user.setName("Admin");
        }
        user.setPassword(passwordEncoder.encode(bootstrapPassword));
        user.setRole(Role.ADMIN);
        user.setStatus("ACTIVE");
        user.setIsActive(true);

        userRepository.save(user);

        System.out.println("🔑 [AdminBootstrap] " + (isNew ? "Created" : "Reset")
                + " admin: " + bootstrapEmail + " (role=ADMIN, status=ACTIVE). "
                + "ลบ env ADMIN_BOOTSTRAP_* ออกได้หลัง login สำเร็จ");
    }
}
