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

            System.out.println(">>> Inspecting event_items columns:");
            DatabaseMetaData metaData = conn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "event_items", null)) {
                while (rs.next()) {
                    System.out.println(
                            "  - Column: " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
                }
            }

            System.out.println(">>> Inspecting event_history existence:");
            try (ResultSet rs = metaData.getTables(null, null, "event_history", null)) {
                if (rs.next()) {
                    System.out.println("✅ event_history table exists.");
                } else {
                    System.out.println("❌ event_history table MISSING.");
                }
            }

            System.out.println(">>> Counting event_items:");
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM event_items")) {
                    if (rs.next()) {
                        System.out.println("  - Count: " + rs.getInt(1));
                    }
                }
            }

            System.out.println(">>> Counting items:");
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM items")) {
                    if (rs.next()) {
                        System.out.println("  - Count: " + rs.getInt(1));
                    }
                }
            }

            System.out.println(">>> Counting events:");
            try (Statement stmt = conn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM events")) {
                    if (rs.next()) {
                        System.out.println("  - Count: " + rs.getInt(1));
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("!!! Error during DB inspection:");
            e.printStackTrace();
        }
        System.out.println(">>> DB Inspection finished.");
    }
}
