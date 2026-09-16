-- Record the destination receiver's reason for every non-GOOD receiving line.
-- Nullable keeps existing GOOD allocations and historical transfers compatible.
ALTER TABLE public.stock_transfer_destination_allocations
    ADD COLUMN IF NOT EXISTS note TEXT;

ALTER TABLE public.stock_transfer_destination_allocations
    ALTER COLUMN note TYPE TEXT;

-- Receipt items already expose a note in the entity; make sure older databases
-- have the column and can retain the same 2,000-character input.
ALTER TABLE IF EXISTS public.inventory_receipt_items
    ADD COLUMN IF NOT EXISTS note TEXT;

DO $migration$
BEGIN
    IF to_regclass('public.inventory_receipt_items') IS NOT NULL THEN
        ALTER TABLE public.inventory_receipt_items
            ALTER COLUMN note TYPE TEXT;
    END IF;
END
$migration$;
