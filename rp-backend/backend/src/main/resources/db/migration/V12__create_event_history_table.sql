-- Ensure event_history table exists and has all columns
CREATE TABLE IF NOT EXISTS event_history (
    id SERIAL PRIMARY KEY,
    event_id BIGINT,
    user_id BIGINT,
    entity_type VARCHAR(255),
    entity_id BIGINT,
    role VARCHAR(255),
    action VARCHAR(255),
    note TEXT,
    change_type VARCHAR(255),
    changed_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW(),
    data JSONB DEFAULT '{}'::jsonb
);

-- Extra safety for missing columns if table already existed partially
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='entity_id') THEN
        ALTER TABLE event_history ADD COLUMN entity_id BIGINT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='role') THEN
        ALTER TABLE event_history ADD COLUMN role VARCHAR(255);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='data') THEN
        ALTER TABLE event_history ADD COLUMN data JSONB DEFAULT '{}'::jsonb;
    END IF;
END $$;
