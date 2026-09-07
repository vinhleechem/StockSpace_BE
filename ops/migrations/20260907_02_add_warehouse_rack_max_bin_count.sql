-- Persist the configured maximum number of Bin slots for each Rack.
-- The migration is additive and intentionally does not rewrite existing
-- warehouse_bins capacity or stock data.

DO $migration$
BEGIN
    IF to_regclass('public.warehouse_racks') IS NULL
       OR to_regclass('public.warehouse_bins') IS NULL THEN
        RAISE EXCEPTION
            'Rack max bin count migration requires warehouse_racks and warehouse_bins';
    END IF;
END
$migration$;

ALTER TABLE public.warehouse_racks
    ADD COLUMN IF NOT EXISTS max_bin_count INTEGER;

-- Existing racks have no historical maximum-slot value. Use their current
-- active Bin count as the conservative compatibility baseline; empty Racks
-- receive one slot. Do not overwrite a value if the migration is re-run.
UPDATE public.warehouse_racks r
SET max_bin_count = GREATEST(
        1,
        COALESCE(
            (
                SELECT COUNT(*)
                FROM public.warehouse_bins b
                WHERE b.rack_id = r.id
                  AND b.is_deleted = false
            ),
            0
        )
    )
WHERE r.max_bin_count IS NULL;

ALTER TABLE public.warehouse_racks
    ALTER COLUMN max_bin_count SET DEFAULT 1,
    ALTER COLUMN max_bin_count SET NOT NULL;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'warehouse_racks'
          AND c.conname = 'ck_warehouse_racks_positive_max_bin_count'
    ) THEN
        ALTER TABLE public.warehouse_racks
            ADD CONSTRAINT ck_warehouse_racks_positive_max_bin_count
            CHECK (max_bin_count >= 1);
    END IF;
END
$migration$;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.warehouse_racks
        WHERE max_bin_count IS NULL OR max_bin_count < 1
    ) THEN
        RAISE EXCEPTION
            'Rack max bin count migration stopped: invalid max_bin_count remains';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.warehouse_racks r
        WHERE (
            SELECT COUNT(*)
            FROM public.warehouse_bins b
            WHERE b.rack_id = r.id
              AND b.is_deleted = false
        ) > r.max_bin_count
    ) THEN
        RAISE EXCEPTION
            'Rack max bin count migration stopped: active Bin count exceeds max_bin_count';
    END IF;
END
$migration$;
