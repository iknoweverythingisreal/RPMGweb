-- Fix event_history table schema
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='note') THEN
        ALTER TABLE event_history ADD COLUMN note TEXT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='action') THEN
        ALTER TABLE event_history ADD COLUMN action VARCHAR(255);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='user_id') THEN
        ALTER TABLE event_history ADD COLUMN user_id BIGINT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='entity_type') THEN
        ALTER TABLE event_history ADD COLUMN entity_type VARCHAR(255);
    END IF;
END $$;
