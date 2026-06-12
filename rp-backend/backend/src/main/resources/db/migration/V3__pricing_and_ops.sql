-- V3__pricing_and_ops.sql
-- เพิ่มคอลัมน์ราคาแม่แบบ (items), snapshot ราคา/ops (event_items),
-- unique index (event_id,item_id), และ CHECK constraint แบบ idempotent

-- =========================
-- ITEMS: base pricing cols
-- =========================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='items' AND column_name='price'
  ) THEN
    ALTER TABLE public.items ADD COLUMN price NUMERIC(12,2) DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='items' AND column_name='rate_type'
  ) THEN
    ALTER TABLE public.items ADD COLUMN rate_type VARCHAR(10) DEFAULT 'PER_DAY';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='items' AND column_name='currency'
  ) THEN
    ALTER TABLE public.items ADD COLUMN currency CHAR(3) DEFAULT 'THB';
  END IF;
END$$;

-- =====================================
-- EVENT_ITEMS: snapshot price + ops cols
-- =====================================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='unit_price'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN unit_price NUMERIC(12,2);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='rate_type'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN rate_type VARCHAR(10);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='line_total'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN line_total NUMERIC(12,2);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='picked_qty'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN picked_qty INTEGER DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='out_qty'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN out_qty INTEGER DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='returned_qty'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN returned_qty INTEGER DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='damaged_qty'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN damaged_qty INTEGER DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='lost_qty'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN lost_qty INTEGER DEFAULT 0;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema='public' AND table_name='event_items' AND column_name='hold_expires_at'
  ) THEN
    ALTER TABLE public.event_items ADD COLUMN hold_expires_at TIMESTAMP;
  END IF;
END$$;

-- =========================================
-- UNIQUE INDEX: one item per event (upsert)
-- =========================================
CREATE UNIQUE INDEX IF NOT EXISTS uniq_event_item
  ON public.event_items(event_id, item_id);

-- ==========================
-- CHECK: non-negative qty
-- ==========================
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'chk_requested_qty_nonneg'
      AND conrelid = 'public.event_items'::regclass
  ) THEN
    ALTER TABLE public.event_items
      ADD CONSTRAINT chk_requested_qty_nonneg
      CHECK (requested_quantity IS NULL OR requested_quantity >= 0);
  END IF;
END$$;
