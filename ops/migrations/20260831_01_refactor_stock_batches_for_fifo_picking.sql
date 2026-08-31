-- Prepare stock batches for FIFO picking without creating a second lot table.
-- This migration is intentionally additive and does not modify older migrations.

DO $migration$
BEGIN
    IF to_regclass('public.stock_batches') IS NULL
       OR to_regclass('public.inventory_receipts') IS NULL
       OR to_regclass('public.inventory_receipt_items') IS NULL
       OR to_regclass('public.inventory_transactions') IS NULL THEN
        RAISE EXCEPTION
            'FIFO picking migration requires stock_batches, inventory_receipts, inventory_receipt_items and inventory_transactions';
    END IF;
END
$migration$;

-- Existing rows may have been created before arrivalDate was populated.
-- Prefer the earliest positive inventory transaction receipt timestamp, then
-- the batch creation timestamp, and only then the migration timestamp.
UPDATE public.stock_batches b
SET arrival_date = COALESCE(history.first_inbound_at, b.created_at, CURRENT_TIMESTAMP::timestamp)
FROM (
    SELECT b2.id,
           MIN(r.created_at) AS first_inbound_at
    FROM public.stock_batches b2
    LEFT JOIN public.inventory_transactions t
           ON t.batch_id = b2.id
          AND t.quantity_changed > 0
          AND t.is_deleted = false
    LEFT JOIN public.inventory_receipts r ON r.id = t.receipt_id
    WHERE b2.arrival_date IS NULL
    GROUP BY b2.id
) history
WHERE b.id = history.id
  AND b.arrival_date IS NULL;

ALTER TABLE public.inventory_receipt_items
    ADD COLUMN IF NOT EXISTS stock_batch_id UUID,
    ADD COLUMN IF NOT EXISTS pick_sequence INTEGER;

-- Backfill historical OUTBOUND items using the strongest available evidence:
-- an existing negative transaction for the same receipt/item location, then
-- the legacy SKU/location lookup. Do not invent a new batch for old history.
WITH outbound_items AS (
    SELECT i.id,
           i.receipt_id,
           i.sku_id,
           i.rack_id,
           i.bin_id,
           r.warehouse_id
    FROM public.inventory_receipt_items i
    JOIN public.inventory_receipts r ON r.id = i.receipt_id
    WHERE r.type = 'OUTBOUND'
      AND i.stock_batch_id IS NULL
), candidates AS (
    SELECT oi.id,
           COALESCE(
               (
                   SELECT t.batch_id
                   FROM public.inventory_transactions t
                   JOIN public.stock_batches transaction_batch
                     ON transaction_batch.id = t.batch_id
                   WHERE t.receipt_id = oi.receipt_id
                     AND t.quantity_changed < 0
                     AND t.is_deleted = false
                     AND transaction_batch.sku_id = oi.sku_id
                     AND transaction_batch.warehouse_id = oi.warehouse_id
                     AND transaction_batch.rack_id IS NOT DISTINCT FROM oi.rack_id
                     AND transaction_batch.bin_id IS NOT DISTINCT FROM oi.bin_id
                   ORDER BY t.created_at NULLS LAST, t.id
                   LIMIT 1
               ),
               (
                   SELECT b.id
                   FROM public.stock_batches b
                   WHERE b.sku_id = oi.sku_id
                     AND b.warehouse_id = oi.warehouse_id
                     AND b.rack_id IS NOT DISTINCT FROM oi.rack_id
                     AND b.bin_id IS NOT DISTINCT FROM oi.bin_id
                     AND b.is_deleted = false
                   ORDER BY b.is_active DESC,
                            b.arrival_date ASC NULLS LAST,
                            b.created_at ASC NULLS LAST,
                            b.id
                   LIMIT 1
               )
           ) AS stock_batch_id
    FROM outbound_items oi
)
UPDATE public.inventory_receipt_items i
SET stock_batch_id = candidates.stock_batch_id
FROM candidates
WHERE i.id = candidates.id
  AND candidates.stock_batch_id IS NOT NULL;

-- A pending OUTBOUND item must remain actionable after deployment. Failing
-- here is safer than selecting a random batch or creating a partial mapping.
DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.inventory_receipt_items i
        JOIN public.inventory_receipts r ON r.id = i.receipt_id
        WHERE r.type = 'OUTBOUND'
          AND r.status = 'PENDING'
          AND i.stock_batch_id IS NULL
    ) THEN
        RAISE EXCEPTION
            'FIFO picking migration stopped: at least one OUTBOUND PENDING item has no deterministic stock batch mapping';
    END IF;
END
$migration$;

-- Preserve a stable legacy sequence for pending outbound items. New picking
-- code will replace this with the routed sequence when it creates/replans.
WITH ranked_items AS (
    SELECT i.id,
           ROW_NUMBER() OVER (
               PARTITION BY i.receipt_id
               ORDER BY i.id
           )::INTEGER AS sequence_number
    FROM public.inventory_receipt_items i
    JOIN public.inventory_receipts r ON r.id = i.receipt_id
    WHERE r.type = 'OUTBOUND'
      AND r.status = 'PENDING'
      AND i.pick_sequence IS NULL
)
UPDATE public.inventory_receipt_items i
SET pick_sequence = ranked_items.sequence_number
FROM ranked_items
WHERE i.id = ranked_items.id;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'inventory_receipt_items'
          AND c.conname = 'ck_inventory_receipt_items_positive_pick_sequence'
    ) THEN
        ALTER TABLE public.inventory_receipt_items
            ADD CONSTRAINT ck_inventory_receipt_items_positive_pick_sequence
            CHECK (pick_sequence IS NULL OR pick_sequence > 0) NOT VALID;
    END IF;
END
$migration$;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.inventory_receipt_items i
        LEFT JOIN public.stock_batches b ON b.id = i.stock_batch_id
        WHERE i.stock_batch_id IS NOT NULL
          AND b.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'FIFO picking migration stopped: receipt item contains an orphan stock_batch_id';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'inventory_receipt_items'
          AND c.conname = 'fk_inventory_receipt_items_stock_batch'
    ) THEN
        ALTER TABLE public.inventory_receipt_items
            ADD CONSTRAINT fk_inventory_receipt_items_stock_batch
            FOREIGN KEY (stock_batch_id) REFERENCES public.stock_batches(id);
    END IF;
END
$migration$;

CREATE INDEX IF NOT EXISTS idx_inventory_receipt_items_stock_batch_id
    ON public.inventory_receipt_items (stock_batch_id);

-- The old index encodes the single-batch-per-location assumption. Drop it only
-- after checking its precondition. On replay the index is already absent, so
-- the migration remains a no-op for this step.
DO $migration$
BEGIN
    IF to_regclass('public.ux_stock_batches_sku_location_active') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM public.stock_batches
            WHERE is_deleted = false
              AND rack_id IS NOT NULL
              AND bin_id IS NOT NULL
            GROUP BY sku_id, warehouse_id, rack_id, bin_id
            HAVING COUNT(*) > 1
        ) THEN
            RAISE EXCEPTION
                'FIFO picking migration stopped: duplicate active stock batches detected before dropping the legacy unique index';
        END IF;

        DROP INDEX public.ux_stock_batches_sku_location_active;
    END IF;
END
$migration$;
