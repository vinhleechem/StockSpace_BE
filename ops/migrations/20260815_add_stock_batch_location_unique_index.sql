DO $migration$
BEGIN
    IF to_regclass('public.stock_batches') IS NOT NULL THEN
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
                'Duplicate active stock batches detected. Run ops/maintenance/warehouse_layout_capacity_preflight.sql before adding the unique index.';
        END IF;

        CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_batches_sku_location_active
            ON public.stock_batches (sku_id, warehouse_id, rack_id, bin_id)
            WHERE is_deleted = false
              AND rack_id IS NOT NULL
              AND bin_id IS NOT NULL;
    END IF;
END
$migration$;
