-- V15: Ensure event_history table is fully aligned with model
DO $$ 
BEGIN
    -- event_id
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='event_id') THEN
        ALTER TABLE event_history ADD COLUMN event_id BIGINT;
    END IF;

    -- user_id
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='user_id') THEN
        ALTER TABLE event_history ADD COLUMN user_id BIGINT;
    END IF;

    -- entity_type
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='entity_type') THEN
        ALTER TABLE event_history ADD COLUMN entity_type VARCHAR(100);
    END IF;

    -- entity_id
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='entity_id') THEN
        ALTER TABLE event_history ADD COLUMN entity_id BIGINT;
    END IF;

    -- role
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='role') THEN
        ALTER TABLE event_history ADD COLUMN role VARCHAR(50);
    END IF;

    -- action
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='action') THEN
        ALTER TABLE event_history ADD COLUMN action VARCHAR(100);
    END IF;

    -- note
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='note') THEN
        ALTER TABLE event_history ADD COLUMN note TEXT;
    END IF;

    -- change_type
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='change_type') THEN
        ALTER TABLE event_history ADD COLUMN change_type VARCHAR(50);
    END IF;

    -- changed_at
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='changed_at') THEN
        ALTER TABLE event_history ADD COLUMN changed_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW();
    END IF;

    -- data
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_history' AND column_name='data') THEN
        ALTER TABLE event_history ADD COLUMN data JSONB DEFAULT '{}'::jsonb;
    END IF;
END $$;
