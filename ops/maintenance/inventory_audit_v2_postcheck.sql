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
