ALTER TABLE event_items
  ADD COLUMN overbook_qty NUMERIC(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN overbook_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
  ADD COLUMN overbook_note TEXT,
  ADD COLUMN overbook_approved_by BIGINT NULL,
  ADD COLUMN overbook_approved_at TIMESTAMP NULL;
