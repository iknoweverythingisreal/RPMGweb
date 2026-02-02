package com.rpmedia.backend;

import java.sql.*;

public class DbInspect {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/rpmedia_db";
        String user = "postgres";
        String password = "rpmedia";

        System.out.println(">>> Starting DB Inspection...");

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println(">>> Connected to DB.");

            String sqlAdmin = "SELECT password FROM users WHERE email = 'admin@rpmedia.com'";
            String adminHash = null;
            try (PreparedStatement ps = conn.prepareStatement(sqlAdmin)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        adminHash = rs.getString("password");
                    }
                }
            }

            if (adminHash != null) {
                String sqlInsert = "INSERT INTO users (email, password, name, role, status, is_active, calendar_color) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sqlInsert)) {
                    ps.setString(1, "techlead@rpmedia.com");
                    ps.setString(2, adminHash);
                    ps.setString(3, "Tech Lead User");
                    ps.setString(4, "TECH_LEAD");
                    ps.setString(5, "ACTIVE");
                    ps.setBoolean(6, true);
                    ps.setString(7, "#FF0000");
                    ps.executeUpdate();
                    System.out.println(">>> Created TECH_LEAD: techlead@rpmedia.com");
                }
            }

        } catch (Exception e) {
            System.err.println("!!! Error during DB inspection:");
            // e.printStackTrace();
            if (e.getMessage().contains("duplicate key")) {
                System.out.println(">>> TECH_LEAD already exists.");
            } else {
                e.printStackTrace();
            }
        }
        System.out.println(">>> DB Inspection finished.");
    }
}
