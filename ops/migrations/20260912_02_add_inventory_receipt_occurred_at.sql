-- Preserve the business occurrence time used by offline movement imports and FIFO.
-- The column is added/backfilled before it becomes NOT NULL so existing rows stay valid.

DO $migration$
BEGIN
    IF to_regclass('public.inventory_receipts') IS NULL THEN
        RAISE EXCEPTION 'Receipt occurrence migration requires inventory_receipts';
    END IF;
END
$migration$;

ALTER TABLE public.inventory_receipts
    ADD COLUMN IF NOT EXISTS occurred_at TIMESTAMP;

UPDATE public.inventory_receipts
SET occurred_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE occurred_at IS NULL;

ALTER TABLE public.inventory_receipts
    ALTER COLUMN occurred_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN occurred_at SET NOT NULL;
