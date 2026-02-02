-- ===========================================
-- V4__serial_and_movements.sql
-- เพิ่มโครงสร้าง Serial ต่อเครื่อง + Movement ประวัติการเคลื่อนไหว
-- ทำให้เช็คของราย "ตัวเครื่อง" ได้ ช่วยเรื่องความถูกต้องก่อนทำราคา
-- ===========================================

-- 1) ตาราง item_units: หน่วยอุปกรณ์รายตัว (serial ต่อชิ้น)
CREATE TABLE IF NOT EXISTS item_units (
  id           SERIAL PRIMARY KEY,
  item_id      INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE,
  serial       VARCHAR(100) NOT NULL,                   -- เลขซีเรียล/รหัสทรัพย์สิน
  status       VARCHAR(20)  NOT NULL DEFAULT 'IN_STOCK',-- IN_STOCK|RESERVED|OUT|RETURNED|LOST|DAMAGED
  condition    VARCHAR(50),                             -- NEW/GOOD/FAIR/BAD อื่นๆ (อิสระ)
  location     VARCHAR(100),                            -- คลัง/โซนเก็บ
  note         TEXT,
  created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  updated_at   TIMESTAMP WITHOUT TIME ZONE
);

-- ป้องกันซีเรียลซ้ำใน item เดียวกัน
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uniq_item_serial'
  ) THEN
    CREATE UNIQUE INDEX uniq_item_serial ON item_units(item_id, serial);
  END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_item_units_item   ON item_units(item_id);
CREATE INDEX IF NOT EXISTS idx_item_units_status ON item_units(status);

-- 2) ตาราง event_item_units: ผูก serial เข้ากับบรรทัดอุปกรณ์ของงาน
CREATE TABLE IF NOT EXISTS event_item_units (
  id             SERIAL PRIMARY KEY,
  event_item_id  INTEGER NOT NULL REFERENCES event_items(id) ON DELETE CASCADE,
  item_unit_id   INTEGER NOT NULL REFERENCES item_units(id) ON DELETE RESTRICT,
  status         VARCHAR(20) NOT NULL DEFAULT 'PICKED',  -- PICKED|OUT|RETURNED
  picked_at      TIMESTAMP WITHOUT TIME ZONE,
  out_at         TIMESTAMP WITHOUT TIME ZONE,
  returned_at    TIMESTAMP WITHOUT TIME ZONE,
  note           TEXT
);

CREATE INDEX IF NOT EXISTS idx_eiu_event_item ON event_item_units(event_item_id);
CREATE INDEX IF NOT EXISTS idx_eiu_item_unit  ON event_item_units(item_unit_id);
CREATE INDEX IF NOT EXISTS idx_eiu_status     ON event_item_units(status);

-- ไม่ให้ unit เดียวถูก "ถือครอง" พร้อมกันหลายงาน (เฉพาะสถานะ PICKED/OUT)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_indexes WHERE schemaname='public' AND indexname='uniq_active_reservation'
  ) THEN
    CREATE UNIQUE INDEX uniq_active_reservation
      ON event_item_units(item_unit_id)
      WHERE status IN ('PICKED','OUT');
  END IF;
END$$;

-- 3) ตาราง inventory_movements: ประวัติของเข้า/ออก/คืน/สูญหาย/เสียหาย/ปรับยอด
CREATE TABLE IF NOT EXISTS inventory_movements (
  id             SERIAL PRIMARY KEY,
  item_unit_id   INTEGER NOT NULL REFERENCES item_units(id) ON DELETE RESTRICT,
  event_id       INTEGER REFERENCES events(id) ON DELETE SET NULL,   -- อาจไม่ผูก event (เช่น ปรับยอด)
  movement_type  VARCHAR(20) NOT NULL,        -- IN|OUT|RETURN|ADJUST|DAMAGE|LOST
  qty            INTEGER NOT NULL DEFAULT 1,  -- สำหรับ serial ปกติคือ 1
  ref_code       VARCHAR(100),                -- หมายเลขใบงาน/เอกสาร/อ้างอิง
  note           TEXT,
  created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_mov_item_unit ON inventory_movements(item_unit_id);
CREATE INDEX IF NOT EXISTS idx_mov_event     ON inventory_movements(event_id);
CREATE INDEX IF NOT EXISTS idx_mov_type_time ON inventory_movements(movement_type, created_at);

-- 4) มุมมองสรุปคงคลังราย item (นับจาก item_units ตามสถานะ)
DROP VIEW IF EXISTS v_item_stock;
CREATE VIEW v_item_stock AS
SELECT
  iu.item_id,
  COUNT(*) FILTER (WHERE iu.status IN ('IN_STOCK','RETURNED'))              AS in_hand,
  COUNT(*) FILTER (WHERE iu.status = 'RESERVED')                            AS reserved,
  COUNT(*) FILTER (WHERE iu.status = 'OUT')                                 AS out_now,
  COUNT(*) FILTER (WHERE iu.status = 'DAMAGED')                             AS damaged,
  COUNT(*) FILTER (WHERE iu.status = 'LOST')                                AS lost,
  COUNT(*)                                                                   AS total_units
FROM item_units iu
GROUP BY iu.item_id;

-- 5) (ทางเลือก) Trigger ช่วยอัปเดตสถานะ item_units เมื่อจับคู่/คืนใน event_item_units
--    *ไม่บังคับใช้ ถ้าทีมอยากควบคุมด้วยแอปชั้นบริการเอง ให้ COMMENT ไว้*
--    เปิดใช้โดยเอา /* ... */ ออก

/*
-- Function: sync item_units.status จาก event_item_units.status
CREATE OR REPLACE FUNCTION fn_sync_unit_status() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.status = 'PICKED' THEN
    UPDATE item_units SET status='RESERVED', updated_at=now() WHERE id = NEW.item_unit_id;
  ELSIF NEW.status = 'OUT' THEN
    UPDATE item_units SET status='OUT', updated_at=now() WHERE id = NEW.item_unit_id;
  ELSIF NEW.status = 'RETURNED' THEN
    UPDATE item_units SET status='IN_STOCK', updated_at=now() WHERE id = NEW.item_unit_id;
  END IF;
  RETURN NEW;
END$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_eiu_after_insupd ON event_item_units;
CREATE TRIGGER trg_eiu_after_insupd
AFTER INSERT OR UPDATE OF status ON event_item_units
FOR EACH ROW EXECUTE FUNCTION fn_sync_unit_status();

-- Function: log movement แบบง่ายจากสถานะใน event_item_units
CREATE OR REPLACE FUNCTION fn_log_movement() RETURNS TRIGGER AS $$
DECLARE
  ev_id INTEGER;
BEGIN
  SELECT e.id INTO ev_id
  FROM event_items ei JOIN events e ON e.id = ei.event_id
  WHERE ei.id = NEW.event_item_id;

  IF NEW.status = 'PICKED' THEN
    INSERT INTO inventory_movements(item_unit_id, event_id, movement_type, qty, note)
    VALUES (NEW.item_unit_id, ev_id, 'IN', 0, 'Picked (reserve)');  -- reserve ไม่กระทบ stock จริง
  ELSIF NEW.status = 'OUT' THEN
    INSERT INTO inventory_movements(item_unit_id, event_id, movement_type, qty, note)
    VALUES (NEW.item_unit_id, ev_id, 'OUT', 1, 'Checked-out');
  ELSIF NEW.status = 'RETURNED' THEN
    INSERT INTO inventory_movements(item_unit_id, event_id, movement_type, qty, note)
    VALUES (NEW.item_unit_id, ev_id, 'RETURN', 1, 'Returned');
  END IF;
  RETURN NEW;
END$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_eiu_log ON event_item_units;
CREATE TRIGGER trg_eiu_log
AFTER INSERT OR UPDATE OF status ON event_item_units
FOR EACH ROW EXECUTE FUNCTION fn_log_movement();
*/

-- 6) (ทางเลือก) Backfill helper:
--    ถ้ายังไม่มี serial แต่มี total_quantity ใน items อยากสร้าง dummy serial เพื่อเริ่มทดสอบ ให้ใช้บล็อกนี้
--    *แนะนำรันครั้งเดียวแล้วลบ/คอมเมนต์ออก*
/*
DO $$
DECLARE
  r RECORD;
  i INT;
  existing INT;
BEGIN
  FOR r IN SELECT id, total_quantity FROM items LOOP
    SELECT COUNT(*) INTO existing FROM item_units WHERE item_id = r.id;
    IF existing = 0 AND r.total_quantity IS NOT NULL AND r.total_quantity > 0 THEN
      i := 1;
      WHILE i <= r.total_quantity LOOP
        INSERT INTO item_units(item_id, serial, status)
        VALUES (r.id, CONCAT('AUTO-', r.id, '-', LPAD(i::TEXT, 4, '0')), 'IN_STOCK');
        i := i + 1;
      END WHILE;
    END IF;
  END LOOP;
END$$;
*/

-- 7) (ทางเลือก) ถ้าต้องการให้ items.total_quantity = จำนวน unit ปัจจุบัน (ไม่นับ LOST)
--    ใช้คิวรีนี้อัปเดตครั้งคราว (หรือสร้างเป็นมุมมอง/trigger เพิ่มเองภายหลัง)
/*
UPDATE items i SET total_quantity = sub.cnt
FROM (
  SELECT item_id, COUNT(*) FILTER (WHERE status <> 'LOST') AS cnt
  FROM item_units GROUP BY item_id
) sub
WHERE sub.item_id = i.id;
*/
