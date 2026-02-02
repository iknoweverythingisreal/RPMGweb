-- V2: columns needed for availability logic + helpful indexes

-- เพิ่มคอลัมน์ที่โค้ดอ้างถึง (ถ้ามีอยู่แล้วคำสั่งนี้จะข้ามเอง)
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS requested_quantity integer;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS status varchar(20);
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS return_date date;

-- ดัชนีช่วยให้คิวรีเร็วขึ้น
CREATE INDEX IF NOT EXISTS idx_events_start_end   ON events(start_date, end_date);
CREATE INDEX IF NOT EXISTS idx_event_items_item   ON event_items(item_id);
CREATE INDEX IF NOT EXISTS idx_event_items_event  ON event_items(event_id);
CREATE INDEX IF NOT EXISTS idx_event_items_status ON event_items(status);



