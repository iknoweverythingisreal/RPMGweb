-- Add missing workflow columns to event_items
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='prepared_by') THEN
        ALTER TABLE event_items ADD COLUMN prepared_by BIGINT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='checked_by') THEN
        ALTER TABLE event_items ADD COLUMN checked_by BIGINT;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='event_items' AND column_name='confirmed_by') THEN
        ALTER TABLE event_items ADD COLUMN confirmed_by BIGINT;
    END IF;
END $$;
