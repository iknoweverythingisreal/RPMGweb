-- ITEMS: รองรับหมวด/รุ่น/UOM/JSONB/สถานะ
ALTER TABLE items
  ADD COLUMN IF NOT EXISTS category        varchar(64)   NOT NULL DEFAULT 'MISC',
  ADD COLUMN IF NOT EXISTS brand           varchar(128),
  ADD COLUMN IF NOT EXISTS model           varchar(128),
  ADD COLUMN IF NOT EXISTS uom             varchar(16)   NOT NULL DEFAULT 'UNIT',  -- UNIT/SQM
  ADD COLUMN IF NOT EXISTS total_quantity  numeric(14,2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS image_url       text,
  ADD COLUMN IF NOT EXISTS description     text,
  ADD COLUMN IF NOT EXISTS spec            jsonb         DEFAULT '{}'::jsonb,
  ADD COLUMN IF NOT EXISTS status          varchar(16)   NOT NULL DEFAULT 'ACTIVE';

DO $$ BEGIN
  CREATE UNIQUE INDEX ux_items_cat_brand_model_desc
  ON items (lower(category), COALESCE(lower(brand),''), COALESCE(lower(model),''), COALESCE(lower(description),''));
EXCEPTION WHEN duplicate_table THEN END $$;

-- EVENT_ITEMS: รองรับ cart/confirm + ราคา
ALTER TABLE event_items
  ADD COLUMN IF NOT EXISTS requested_quantity int            NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS allocated_quantity int            NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS status             varchar(16)    NOT NULL DEFAULT 'DRAFT',
  ADD COLUMN IF NOT EXISTS unit_price         numeric(12,2)           DEFAULT 0,
  ADD COLUMN IF NOT EXISTS rate_type          varchar(16)             DEFAULT 'PER_EVENT', -- หรือ PER_DAY
  ADD COLUMN IF NOT EXISTS line_total         numeric(12,2)           DEFAULT 0,
  ADD COLUMN IF NOT EXISTS metadata           jsonb                   DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_event_items_event_item ON event_items(event_id, item_id);
