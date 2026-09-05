-- Read-only checks before applying the inventory audit v2 migration.
SELECT to_regclass('public.inventory_audits') IS NOT NULL AS inventory_audits_present;
SELECT to_regclass('public.inventory_audit_items') IS NOT NULL AS inventory_audit_items_present;
SELECT to_regclass('public.inventory_receipts') IS NOT NULL AS inventory_receipts_present;
SELECT to_regclass('public.stock_batches') IS NOT NULL AS stock_batches_present;

SELECT status, COUNT(*) AS audit_count
FROM public.inventory_audits
WHERE is_deleted = false
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS orphan_audit_items
FROM public.inventory_audit_items i
LEFT JOIN public.inventory_audits a ON a.id = i.audit_id
WHERE a.id IS NULL;
