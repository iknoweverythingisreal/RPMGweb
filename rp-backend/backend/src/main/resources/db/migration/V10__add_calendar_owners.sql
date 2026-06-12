-- Create calendar_owners table
CREATE TABLE IF NOT EXISTS calendar_owners (
    id SERIAL PRIMARY KEY,
    teamup_subcalendar_id BIGINT UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    color_hex VARCHAR(7),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Update events table
ALTER TABLE events ADD COLUMN IF NOT EXISTS calendar_owner_id INTEGER REFERENCES calendar_owners(id);
ALTER TABLE events ADD COLUMN IF NOT EXISTS teamup_subcalendar_ids JSONB DEFAULT '[]'::jsonb;

-- Ensure uniqueness for Teamup events to prevent duplicates
-- Using a DO block to safely add the constraint if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_events_external_id') THEN
        ALTER TABLE events ADD CONSTRAINT uq_events_external_id UNIQUE (external_source, external_id);
    END IF;
END $$;
