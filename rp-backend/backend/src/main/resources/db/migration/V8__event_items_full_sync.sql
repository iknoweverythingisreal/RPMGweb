-- ================================
-- V8 : Sync event_items & events schema
-- ================================

-- ========== EVENT_ITEMS extra workflow columns ==========
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS allocated_at TIMESTAMP;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS prepared_at TIMESTAMP;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS checked_at TIMESTAMP;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS returned_at TIMESTAMP;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS returned_quantity INTEGER;

ALTER TABLE event_items ADD COLUMN IF NOT EXISTS rent_price NUMERIC(12,2);
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS rent_vendor VARCHAR(255);

ALTER TABLE event_items ADD COLUMN IF NOT EXISTS serials JSONB;

ALTER TABLE event_items ADD COLUMN IF NOT EXISTS picked_qty INTEGER;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS out_qty INTEGER;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS damaged_qty INTEGER;
ALTER TABLE event_items ADD COLUMN IF NOT EXISTS lost_qty INTEGER;

ALTER TABLE event_items ADD COLUMN IF NOT EXISTS hold_expires_at TIMESTAMP;

-- Make sure status column always VARCHAR for enum mapping
ALTER TABLE event_items
    ALTER COLUMN status TYPE VARCHAR(32);


-- ========== EVENT_ITEMS foreign keys ==========
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_event_items_event'
    ) THEN
        ALTER TABLE event_items
            ADD CONSTRAINT fk_event_items_event
            FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE;
    END IF;
END$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_event_items_item'
    ) THEN
        ALTER TABLE event_items
            ADD CONSTRAINT fk_event_items_item
            FOREIGN KEY (item_id) REFERENCES items(id);
    END IF;
END$$;


-- ========== EVENTS workflow & overbooking ==========
ALTER TABLE events ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
ALTER TABLE events ADD COLUMN IF NOT EXISTS prepared_at TIMESTAMP;
ALTER TABLE events ADD COLUMN IF NOT EXISTS checked_at TIMESTAMP;
ALTER TABLE events ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;

ALTER TABLE events ADD COLUMN IF NOT EXISTS overbook_requested BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS overbook_approved BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS overbook_approved_by BIGINT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS overbook_approved_at TIMESTAMP;
ALTER TABLE events ADD COLUMN IF NOT EXISTS overbook_note TEXT;

ALTER TABLE events ADD COLUMN IF NOT EXISTS rent_requested BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS rent_approved BOOLEAN DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS rent_approved_by BIGINT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS rent_approved_at TIMESTAMP;
ALTER TABLE events ADD COLUMN IF NOT EXISTS rent_note TEXT;


-- ========== EVENTS: Tech Lead ==========
ALTER TABLE events ADD COLUMN IF NOT EXISTS tech_lead_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_events_tech_lead'
    ) THEN
        ALTER TABLE events
            ADD CONSTRAINT fk_events_tech_lead
            FOREIGN KEY (tech_lead_id) REFERENCES users(id);
    END IF;
END$$;

-- ================================
-- END V8
-- ================================
