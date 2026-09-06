-- Fail fast if the application was deployed without the canonical audit schema.
-- This is intentionally metadata-only: it does not mutate business data.
DO $$
DECLARE
    missing_columns TEXT;
    missing_tables TEXT;
    status_constraint TEXT;
BEGIN
    SELECT string_agg(format('%s.%s', required.table_name, required.column_name), ', ')
    INTO missing_columns
    FROM (
        VALUES
            ('inventory_audits', 'tenant_id'),
            ('inventory_audits', 'assigned_to'),
            ('inventory_audits', 'scope_type'),
            ('inventory_audits', 'scope_rack_id'),
            ('inventory_audits', 'scope_bin_id'),
            ('inventory_audits', 'count_round'),
            ('inventory_audits', 'started_at'),
            ('inventory_audits', 'submitted_at'),
            ('inventory_audits', 'reviewed_at'),
            ('inventory_audits', 'cancelled_at'),
            ('inventory_audits', 'review_reason'),
            ('inventory_audits', 'version'),
            ('inventory_audit_items', 'sku_id'),
            ('inventory_audit_items', 'rack_id'),
            ('inventory_audit_items', 'bin_id'),
            ('inventory_audit_items', 'count_status'),
            ('inventory_audit_items', 'count_round'),
            ('inventory_audit_items', 'counted_by'),
            ('inventory_audit_items', 'counted_at'),
            ('inventory_audit_items', 'variance_reason'),
            ('inventory_audit_adjustments', 'batch_id')
    ) AS required(table_name, column_name)
    LEFT JOIN information_schema.columns actual
      ON actual.table_schema = 'public'
     AND actual.table_name = required.table_name
     AND actual.column_name = required.column_name
    WHERE actual.column_name IS NULL;

    SELECT string_agg(required.table_name, ', ')
    INTO missing_tables
    FROM (VALUES
        ('inventory_audit_locks'),
        ('inventory_audit_adjustments')
    ) AS required(table_name)
    WHERE to_regclass('public.' || required.table_name) IS NULL;

    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'Inventory audit schema is missing tables: %', missing_tables;
    END IF;
    IF missing_columns IS NOT NULL THEN
        RAISE EXCEPTION 'Inventory audit schema is missing columns: %', missing_columns;
    END IF;

    SELECT pg_get_constraintdef(oid)
    INTO status_constraint
    FROM pg_constraint
    WHERE conrelid = 'public.inventory_audits'::regclass
      AND conname = 'inventory_audits_status_check';

    IF status_constraint IS NULL
       OR status_constraint NOT LIKE '%DRAFT%'
       OR status_constraint NOT LIKE '%IN_PROGRESS%'
       OR status_constraint NOT LIKE '%RECOUNT_REQUIRED%'
       OR status_constraint NOT LIKE '%CANCELLED%' THEN
        RAISE EXCEPTION 'Inventory audit status constraint is missing canonical statuses';
    END IF;
END $$;

-- All count_value results must be zero; the final rows are informational.
SELECT 'orphan_audit_locks' AS check_name, COUNT(*) AS count_value
FROM public.inventory_audit_locks l
LEFT JOIN public.inventory_audits a ON a.id = l.audit_id
WHERE a.id IS NULL;

SELECT 'orphan_audit_adjustments' AS check_name, COUNT(*) AS count_value
FROM public.inventory_audit_adjustments x
LEFT JOIN public.inventory_audits a ON a.id = x.audit_id
LEFT JOIN public.inventory_audit_items i ON i.id = x.audit_item_id
LEFT JOIN public.inventory_receipts r ON r.id = x.receipt_id
LEFT JOIN public.stock_batches b ON b.id = x.batch_id
WHERE a.id IS NULL OR i.id IS NULL OR r.id IS NULL OR b.id IS NULL;

SELECT 'active_lock_count' AS check_name, COUNT(*) AS count_value
FROM public.inventory_audit_locks
WHERE released_at IS NULL AND is_active = true AND is_deleted = false;

SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname = 'ux_inventory_audit_locks_active_warehouse';
