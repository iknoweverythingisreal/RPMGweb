-- V5__add_missing_item_columns.sql
-- เติมคอลัมน์ที่ entity/โค้ดใช้งาน แต่ schema ปัจจุบันไม่มี

ALTER TABLE items
  ADD COLUMN IF NOT EXISTS brand        VARCHAR(100),
  ADD COLUMN IF NOT EXISTS model        VARCHAR(100),
  ADD COLUMN IF NOT EXISTS category     VARCHAR(50),
  ADD COLUMN IF NOT EXISTS uom          VARCHAR(20),
  ADD COLUMN IF NOT EXISTS status       VARCHAR(20) DEFAULT 'ACTIVE';

-- ใครที่อยากให้ค่าเริ่มต้นชัด ๆ สำหรับของเก่า
UPDATE items SET status = COALESCE(status, 'ACTIVE');
