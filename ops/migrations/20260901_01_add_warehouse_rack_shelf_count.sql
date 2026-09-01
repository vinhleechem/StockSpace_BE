-- Add the total shelf count to warehouse racks.
-- The migration is additive and keeps existing rack/bin structure intact.

DO $migration$
BEGIN
    IF to_regclass('public.warehouse_racks') IS NULL
       OR to_regclass('public.warehouse_bins') IS NULL THEN
        RAISE EXCEPTION
            'Rack shelf count migration requires warehouse_racks and warehouse_bins';
    END IF;
END
$migration$;

-- Legacy bins may not have a usable shelf level. Preserve the existing
-- structure with the same application fallback: the first shelf.
UPDATE public.warehouse_bins
SET shelf_level = 1
WHERE shelf_level IS NULL OR shelf_level < 1;

ALTER TABLE public.warehouse_racks
    ADD COLUMN IF NOT EXISTS shelf_count INTEGER;

-- Infer the smallest valid count that describes the existing active bins.
-- Racks without bins still have one available shelf by default.
UPDATE public.warehouse_racks r
SET shelf_count = GREATEST(
        1,
        COALESCE(
            (
                SELECT MAX(b.shelf_level)
                FROM public.warehouse_bins b
                WHERE b.rack_id = r.id
                  AND b.is_deleted = false
            ),
            1
        )
    )
WHERE r.shelf_count IS NULL;

ALTER TABLE public.warehouse_racks
    ALTER COLUMN shelf_count SET DEFAULT 1,
    ALTER COLUMN shelf_count SET NOT NULL;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'warehouse_racks'
          AND c.conname = 'ck_warehouse_racks_positive_shelf_count'
    ) THEN
        ALTER TABLE public.warehouse_racks
            ADD CONSTRAINT ck_warehouse_racks_positive_shelf_count
            CHECK (shelf_count >= 1);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        WHERE n.nspname = 'public'
          AND t.relname = 'warehouse_bins'
          AND c.conname = 'ck_warehouse_bins_positive_shelf_level'
    ) THEN
        ALTER TABLE public.warehouse_bins
            ADD CONSTRAINT ck_warehouse_bins_positive_shelf_level
            CHECK (shelf_level >= 1);
    END IF;
END
$migration$;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.warehouse_racks
        WHERE shelf_count IS NULL OR shelf_count < 1
    ) THEN
        RAISE EXCEPTION
            'Rack shelf count migration stopped: invalid shelf_count remains';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.warehouse_bins b
        WHERE b.shelf_level IS NULL OR b.shelf_level < 1
    ) THEN
        RAISE EXCEPTION
            'Rack shelf count migration stopped: invalid shelf_level remains';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.warehouse_bins b
        JOIN public.warehouse_racks r ON r.id = b.rack_id
        WHERE b.shelf_level > r.shelf_count
    ) THEN
        RAISE EXCEPTION
            'Rack shelf count migration stopped: a bin exceeds its rack shelf_count';
    END IF;
END
$migration$;
