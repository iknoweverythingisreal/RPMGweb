package com.rpmedia.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FlywayRepairRunner implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            System.out.println("🛠️ [FlywayRepair] Attempting to fix schema history...");
            jdbcTemplate.execute("DELETE FROM flyway_schema_history WHERE version = '9'");
            System.out.println("✅ [FlywayRepair] Successfully deleted version 9 from schema history.");
        } catch (Exception e) {
            System.out.println("⚠️ [FlywayRepair] Could not delete version 9 (it might not exist yet): " + e.getMessage());
        }
    }
}
