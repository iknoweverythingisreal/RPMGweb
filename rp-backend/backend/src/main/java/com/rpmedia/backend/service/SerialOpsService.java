package com.rpmedia.backend.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rpmedia.backend.dto.SerialAvailabilityDTO;
import com.rpmedia.backend.dto.SerialUnitDTO;

import java.util.ArrayList;
import java.util.List;

@Service
public class SerialOpsService {
  private final JdbcTemplate jdbc;

  public SerialOpsService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private void assertEventItemBelongsToEvent(Long eventItemId, Long eventId) {
    Long found = jdbc.queryForObject(
        "SELECT COUNT(*) FROM event_items WHERE id=? AND event_id=?",
        Long.class, eventItemId, eventId);
    if (found == null || found == 0)
      throw new IllegalArgumentException("eventItem not in event");
  }

  private Long getEventItemItemId(Long eventItemId) {
    return jdbc.queryForObject("SELECT item_id FROM event_items WHERE id=?", Long.class, eventItemId);
  }

  private Long getEventIdOfEventItem(Long eventItemId) {
    return jdbc.queryForObject("SELECT event_id FROM event_items WHERE id=?", Long.class, eventItemId);
  }

  private void assertUnitsBelongToItem(List<Long> unitIds, Long itemId) {
    long ok = 0;
    for (Long uid : unitIds) {
      Long c = jdbc.queryForObject("SELECT COUNT(*) FROM item_units WHERE id=? AND item_id=?", Long.class, uid, itemId);
      if (c != null && c == 1)
        ok++;
    }
    if (ok != unitIds.size())
      throw new IllegalArgumentException("unitIds not match item");
  }

  public List<Long> findAllUnits(Long itemId) {
    return jdbc.queryForList(
        "SELECT id FROM item_units WHERE item_id=? ORDER BY id",
        Long.class,
        itemId);
  }

  public List<Long> findAvailableUnits(Long itemId) {
    return jdbc.queryForList(
        "SELECT id FROM item_units WHERE item_id=? AND status='AVAILABLE' ORDER BY id",
        Long.class,
        itemId);
  }

  public List<Long> findUnitsInUse(Long itemId, Long eventId, String start, String end) {
    return jdbc.queryForList("""
            SELECT eiu.item_unit_id
            FROM event_item_units eiu
            JOIN event_items ei ON ei.id = eiu.event_item_id
            JOIN events e ON e.id = ei.event_id
            WHERE ei.item_id = ?
              AND e.id <> ?
              AND e.start_date <= ?
              AND e.end_date >= ?
              AND (ei.status IS NULL OR (ei.status <> 'CANCELLED' AND ei.status <> 'RETURNED'))
              AND eiu.status IN ('PICKED', 'OUT', 'RESERVED')
        """,
        Long.class,
        itemId, eventId, end, start // end date against start_date, start date against end_date
    );
  }

  public List<SerialAvailabilityDTO> getAvailability(Long itemId, Long eventId, String start, String end) {

    List<Long> allUnits = findAllUnits(itemId);
    List<Long> inUseByOthers = findUnitsInUse(itemId, eventId, start, end);
    List<Long> availableUnits = findAvailableUnits(itemId);

    // Find units already booked/reserved for THIS event
    List<Long> bookedBySelf = new ArrayList<>();
    if (eventId != null) {
      bookedBySelf = jdbc.queryForList("""
              SELECT eiu.item_unit_id
              FROM event_item_units eiu
              JOIN event_items ei ON ei.id = eiu.event_item_id
              WHERE ei.item_id = ?
                AND ei.event_id = ?
                AND (ei.status IS NULL OR (ei.status <> 'CANCELLED' AND ei.status <> 'RETURNED'))
          """, Long.class, itemId, eventId);
    }

    List<SerialAvailabilityDTO> result = new ArrayList<>();

    for (Long uid : allUnits) {
      SerialAvailabilityDTO dto = new SerialAvailabilityDTO();
      dto.setItemUnitId(uid);

      // Fetch serial string
      String serial = jdbc.queryForObject(
          "SELECT serial FROM item_units WHERE id=?",
          String.class,
          uid);
      dto.setSerial(serial);

      if (inUseByOthers.contains(uid)) {
        dto.setStatus("IN_USE"); // Used by another event
      } else if (bookedBySelf.contains(uid)) {
        dto.setStatus("BOOKED_SELF"); // Already booked for this event
      } else if (availableUnits.contains(uid)) {
        dto.setStatus("AVAILABLE");
      } else {
        dto.setStatus("UNAVAILABLE"); // Damaged, etc.
      }

      result.add(dto);
    }

    return result;
  }

  @Transactional
  public int linkUnitsPicked(Long eventId, Long eventItemId, List<Long> unitIds, String note) {
    assertEventItemBelongsToEvent(eventItemId, eventId);
    Long itemId = getEventItemItemId(eventItemId);
    assertUnitsBelongToItem(unitIds, itemId);

    int linked = 0;
    for (Long uid : unitIds) {
      try {
        jdbc.update(
            "INSERT INTO event_item_units(event_item_id, item_unit_id, status, picked_at, note) " +
                "VALUES (?, ?, 'PICKED', now(), ?)",
            eventItemId, uid, note);
        // กันถือซ้อน: partial unique index uniq_active_reservation จะคัดกรองให้อยู่แล้ว
        jdbc.update("UPDATE item_units SET status='RESERVED', updated_at=now() WHERE id=?", uid);
        linked++;
      } catch (DuplicateKeyException ignore) {
        // หากซ้ำ (ถูกถืออยู่) ข้ามไป
      }
    }
    if (linked > 0) {
      jdbc.update("UPDATE event_items SET picked_qty = COALESCE(picked_qty,0) + ? WHERE id=?", linked, eventItemId);
    }
    return linked;
  }

  @Transactional
  public int checkout(Long eventId, Long eventItemId, List<Long> unitIds, Long userId, String note) {
    assertEventItemBelongsToEvent(eventItemId, eventId);
    int affected = 0;
    for (Long uid : unitIds) {
      int upd = jdbc.update(
          "UPDATE event_item_units SET status='OUT', out_at=now(), note=COALESCE(note,'') || CASE WHEN ? IS NULL THEN '' ELSE ' '||? END "
              +
              "WHERE event_item_id=? AND item_unit_id=? AND status IN ('PICKED','OUT')",
          note, note, eventItemId, uid);
      if (upd > 0) {
        jdbc.update("INSERT INTO inventory_movements(item_unit_id, event_id, movement_type, qty, note, created_by) " +
            "VALUES (?, ?, 'OUT', 1, ?, ?)", uid, eventId, note, userId);
        jdbc.update("UPDATE item_units SET status='OUT', updated_at=now() WHERE id=?", uid);
        affected += upd;
      }
    }
    if (affected > 0) {
      jdbc.update("UPDATE event_items SET out_qty = COALESCE(out_qty,0) + ? WHERE id=?", affected, eventItemId);
    }
    return affected;
  }

  @Transactional
  public int doReturn(Long eventId, Long eventItemId, List<Long> unitIds, Long userId, String note) {
    assertEventItemBelongsToEvent(eventItemId, eventId);
    int affected = 0;
    for (Long uid : unitIds) {
      int upd = jdbc.update(
          "UPDATE event_item_units SET status='RETURNED', returned_at=now(), note=COALESCE(note,'') || CASE WHEN ? IS NULL THEN '' ELSE ' '||? END "
              +
              "WHERE event_item_id=? AND item_unit_id=? AND status='OUT'",
          note, note, eventItemId, uid);
      if (upd > 0) {
        jdbc.update("INSERT INTO inventory_movements(item_unit_id, event_id, movement_type, qty, note, created_by) " +
            "VALUES (?, ?, 'RETURN', 1, ?, ?)", uid, eventId, note, userId);
        jdbc.update("UPDATE item_units SET status='RETURNED', updated_at=now() WHERE id=?", uid);
        affected += upd;
      }
    }
    if (affected > 0) {
      jdbc.update("UPDATE event_items SET returned_qty = COALESCE(returned_qty,0) + ? WHERE id=?", affected,
          eventItemId);
    }
    return affected;
  }

  @Transactional
  public int markDamage(Long eventId, List<Long> unitIds, Long userId, String note) {
    int affected = 0;
    for (Long uid : unitIds) {
      Long ev = getEventIdOfEventItem(
          jdbc.queryForObject(
              "SELECT event_item_id FROM event_item_units WHERE item_unit_id=? ORDER BY id DESC LIMIT 1",
              Long.class, uid));
      if (ev != null && ev.equals(eventId)) {
        jdbc.update("INSERT INTO inventory_movements(item_unit_id, event_id, movement_type, qty, note, created_by) " +
            "VALUES (?, ?, 'DAMAGE', 1, ?, ?)", uid, eventId, note, userId);
        jdbc.update("UPDATE item_units SET status='DAMAGED', updated_at=now() WHERE id=?", uid);
        affected++;
      }
    }
    return affected;
  }

  // =============================
  // NEW: GET SERIALS FOR EVENT-ITEM
  // =============================
  public List<SerialUnitDTO> getUnitsByEventItem(Long eventItemId) {
    return jdbc.query("""
            SELECT
                eiu.item_unit_id,
                iu.serial,
                eiu.status,
                eiu.picked_at,
                eiu.out_at,
                eiu.returned_at,
                eiu.note
            FROM event_item_units eiu
            JOIN item_units iu ON iu.id = eiu.item_unit_id
            WHERE eiu.event_item_id = ?
            ORDER BY eiu.id
        """,
        (rs, rowNum) -> new SerialUnitDTO(
            rs.getLong("item_unit_id"),
            rs.getString("serial"),
            rs.getString("status"),
            rs.getTimestamp("picked_at") != null ? rs.getTimestamp("picked_at").toLocalDateTime() : null,
            rs.getTimestamp("out_at") != null ? rs.getTimestamp("out_at").toLocalDateTime() : null,
            rs.getTimestamp("returned_at") != null ? rs.getTimestamp("returned_at").toLocalDateTime() : null,
            rs.getString("note")),
        eventItemId);
  }

}
