-- V13: Sync event_items table with Java Model (adding missing columns)
DO $$ 
BEGIN
    -- Add version column for optimistic locking (@Version)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='version') THEN
        ALTER TABLE event_items ADD COLUMN version BIGINT DEFAULT 0;
    END IF;

    -- Add quantity column (missing but exists in model)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='quantity') THEN
        ALTER TABLE event_items ADD COLUMN quantity INTEGER DEFAULT 0;
    END IF;

    -- Add team_id column
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='team_id') THEN
        ALTER TABLE event_items ADD COLUMN team_id BIGINT;
    END IF;

    -- Add source column
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='source') THEN
        ALTER TABLE event_items ADD COLUMN source VARCHAR(100);
    END IF;

    -- Add created_at/updated_at
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='created_at') THEN
        ALTER TABLE event_items ADD COLUMN created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='updated_at') THEN
        ALTER TABLE event_items ADD COLUMN updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();
    END IF;

    -- Add remark
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='remark') THEN
        ALTER TABLE event_items ADD COLUMN remark TEXT;
    END IF;

    -- Add overbook columns to events just in case (sync with model)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='events' AND column_name='version') THEN
        ALTER TABLE events ADD COLUMN version INTEGER DEFAULT 1;
    END IF;

END $$;
