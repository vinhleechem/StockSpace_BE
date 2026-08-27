-- Read-only schema preflight. Run before migration_baseline.sh --apply.
-- Missing objects are blockers; this script must not be bypassed.

DO $$
DECLARE
    required_table text;
    required_tables text[] := ARRAY[
        'chat_sessions', 'chat_messages', 'system_knowledge',
        'warehouse_layouts', 'warehouse_racks', 'warehouse_bins',
        'product_skus', 'stock_batches', 'inventory_transactions',
        'rental_contracts', 'subscriptions', 'service_packages'
    ];
BEGIN
    FOREACH required_table IN ARRAY required_tables LOOP
        IF to_regclass(format('public.%I', required_table)) IS NULL THEN
            RAISE EXCEPTION 'Migration baseline preflight failed: missing table public.%', required_table;
        END IF;
    END LOOP;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'warehouse_layouts'
          AND column_name IN ('length', 'positions')
        GROUP BY table_name HAVING COUNT(*) = 2
    ) THEN
        RAISE EXCEPTION 'Migration baseline preflight failed: layout meter/positions columns missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'product_skus'
          AND column_name IN ('unit_weight_kg', 'unit_volume_m3')
        GROUP BY table_name HAVING COUNT(*) = 2
    ) THEN
        RAISE EXCEPTION 'Migration baseline preflight failed: SKU physical-property columns missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = 'public' AND indexname = 'ux_stock_batches_sku_location_active'
    ) THEN
        RAISE EXCEPTION 'Migration baseline preflight failed: stock location/SKU unique index missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_warehouse_layouts_positive_dimensions'
    ) THEN
        RAISE EXCEPTION 'Migration baseline preflight failed: layout geometry constraint missing';
    END IF;

    RAISE NOTICE 'Migration baseline preflight passed; no data was changed';
END $$;
