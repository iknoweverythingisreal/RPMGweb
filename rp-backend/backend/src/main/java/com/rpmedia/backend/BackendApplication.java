package com.rpmedia.backend;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.rpmedia.backend.service.EventLifecycleService;

@SpringBootApplication
@EnableScheduling

public class BackendApplication {
  @PostConstruct
  public void started() {
    System.out.println(">>> STARTED from: com.rpmedia.backend");
  }

  @org.springframework.context.annotation.Bean
  public org.springframework.boot.CommandLineRunner fixData(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
    return args -> {
      // Fix User roles
      jdbcTemplate.update("UPDATE users SET role = 'EMPLOYEE' WHERE role = 'USER'");

      // Fix Item status
      try {
        jdbcTemplate.execute("ALTER TABLE items ALTER COLUMN status TYPE varchar(50)");
      } catch (Exception e) {
        /* already varchar or other error */ }
      jdbcTemplate.update("UPDATE items SET status = 'AVAILABLE' WHERE status = '0' OR status IS NULL");
      jdbcTemplate.update("UPDATE items SET status = 'IN_USE' WHERE status = '1'");
      jdbcTemplate.update("UPDATE items SET status = 'BROKEN' WHERE status = '2'");
      jdbcTemplate.update("UPDATE items SET status = 'UNDER_REPAIR' WHERE status = '3'");
      jdbcTemplate.update("UPDATE items SET status = 'LOST' WHERE status = '4'");

      // Fix EventItem status
      jdbcTemplate.update("UPDATE event_items SET status = 'CONFIRMED' WHERE status = '0' OR status IS NULL");

      // Drop view that depends on item_units.status
      jdbcTemplate.execute("DROP VIEW IF EXISTS v_item_stock CASCADE");

      // Fix ItemUnit status
      try {
        jdbcTemplate.execute("ALTER TABLE item_units ALTER COLUMN status TYPE varchar(50)");
      } catch (Exception e) {
        /* already varchar or other error */ }
      jdbcTemplate.update("UPDATE item_units SET status = 'AVAILABLE' WHERE status = '0' OR status IS NULL");
      jdbcTemplate.update("UPDATE item_units SET status = 'IN_USE' WHERE status = '1'");
      jdbcTemplate.update("UPDATE item_units SET status = 'BROKEN' WHERE status = '2'");
      jdbcTemplate.update("UPDATE item_units SET status = 'UNDER_REPAIR' WHERE status = '3'");
      jdbcTemplate.update("UPDATE item_units SET status = 'LOST' WHERE status = '4'");

      // Recreate view
      jdbcTemplate.execute("""
            CREATE OR REPLACE VIEW v_item_stock AS
            SELECT
              iu.item_id,
              COUNT(*) FILTER (WHERE iu.status IN ('IN_STOCK','RETURNED','AVAILABLE'))   AS in_hand,
              COUNT(*) FILTER (WHERE iu.status = 'RESERVED')                            AS reserved,
              COUNT(*) FILTER (WHERE iu.status IN ('OUT','IN_USE'))                     AS out_now,
              COUNT(*) FILTER (WHERE iu.status IN ('DAMAGED','BROKEN'))                 AS damaged,
              COUNT(*) FILTER (WHERE iu.status = 'LOST')                                AS lost,
              COUNT(*)                                                                   AS total_units
            FROM item_units iu
            GROUP BY iu.item_id
          """);

      // Fix EventItemUnit status
      try {
        jdbcTemplate.execute("ALTER TABLE event_item_units ALTER COLUMN status TYPE varchar(50)");
      } catch (Exception e) {
        /* already varchar or other error */ }
      jdbcTemplate.update("UPDATE event_item_units SET status = 'AVAILABLE' WHERE status = '0' OR status IS NULL");
      jdbcTemplate.update("UPDATE event_item_units SET status = 'IN_USE' WHERE status = '1'");
      jdbcTemplate.update("UPDATE event_item_units SET status = 'BROKEN' WHERE status = '2'");
      jdbcTemplate.update("UPDATE event_item_units SET status = 'UNDER_REPAIR' WHERE status = '3'");
      jdbcTemplate.update("UPDATE event_item_units SET status = 'LOST' WHERE status = '4'");

      System.out.println("DEBUG: items status counts:");
      jdbcTemplate.queryForList("SELECT status, count(*) as cnt FROM items GROUP BY status")
          .forEach(row -> System.out.println("  " + row.get("status") + ": " + row.get("cnt")));

      System.out.println("DEBUG: item_units status counts:");
      jdbcTemplate.queryForList("SELECT status, count(*) as cnt FROM item_units GROUP BY status")
          .forEach(row -> System.out.println("  " + row.get("status") + ": " + row.get("cnt")));

      System.out.println("DEBUG: Tables with 'status' column:");
      jdbcTemplate.queryForList("""
              SELECT table_name, column_name, data_type
              FROM information_schema.columns
              WHERE column_name = 'status'
                AND table_schema = 'public'
          """)
          .forEach(row -> System.out.println(
              "  " + row.get("table_name") + "." + row.get("column_name") + " (" + row.get("data_type") + ")"));

      System.out.println("DEBUG: event_item_units columns:");
      jdbcTemplate.queryForList("""
              SELECT column_name, data_type
              FROM information_schema.columns
              WHERE table_name = 'event_item_units'
                AND table_schema = 'public'
          """)
          .forEach(row -> System.out.println("  " + row.get("column_name") + " (" + row.get("data_type") + ")"));

      // Fix missing columns in event_item_units
      try {
        jdbcTemplate.execute(
            "ALTER TABLE event_item_units ADD COLUMN IF NOT EXISTS created_at timestamp without time zone DEFAULT now()");
        jdbcTemplate.execute(
            "ALTER TABLE event_item_units ADD COLUMN IF NOT EXISTS updated_at timestamp without time zone DEFAULT now()");
      } catch (Exception e) {
        /* already exists or other error */ }

      System.out.println("DEBUG: Users and roles:");
      jdbcTemplate.queryForList("SELECT email, role, status, is_active FROM users")
          .forEach(row -> System.out.println("  " + row.get("email") + ": role=" + row.get("role") + ", status="
              + row.get("status") + ", isActive=" + row.get("is_active")));

      System.out.println("Fixed invalid roles and statuses in database.");
    };
  }

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }

  @Autowired
  private EventLifecycleService lifecycleService;

  @Scheduled(cron = "0 0 0 * * *")
  public void autoUpdateLifecycle() {
    lifecycleService.updateEventStatuses();
  }

}
