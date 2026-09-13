-- Read-only post-deployment verification for WMS import staging.
-- Expected count_value is zero.

SELECT 'missing_import_jobs_table' AS check_name,
       CASE WHEN to_regclass('public.wms_import_jobs') IS NULL THEN 1 ELSE 0 END AS count_value;

SELECT 'missing_import_rows_table' AS check_name,
       CASE WHEN to_regclass('public.wms_import_rows') IS NULL THEN 1 ELSE 0 END AS count_value;

SELECT 'orphan_import_rows' AS check_name, COUNT(*) AS count_value
FROM public.wms_import_rows r
LEFT JOIN public.wms_import_jobs j ON j.id = r.job_id
WHERE j.id IS NULL;

SELECT 'invalid_import_job_scope' AS check_name, COUNT(*) AS count_value
FROM public.wms_import_jobs
WHERE NOT (
    (import_type = 'SKU_CATALOG' AND warehouse_id IS NULL AND audit_id IS NULL)
    OR (import_type = 'OFFLINE_MOVEMENT' AND warehouse_id IS NOT NULL AND audit_id IS NULL)
    OR (import_type = 'AUDIT_RECONCILIATION' AND warehouse_id IS NOT NULL AND audit_id IS NOT NULL)
);

SELECT 'missing_applied_content_index' AS check_name,
       CASE WHEN EXISTS (
           SELECT 1 FROM pg_indexes
           WHERE schemaname = 'public'
             AND indexname = 'ux_wms_import_jobs_applied_content'
       ) THEN 0 ELSE 1 END AS count_value;

SELECT 'missing_import_row_key_index' AS check_name,
       CASE WHEN EXISTS (
           SELECT 1 FROM pg_indexes
           WHERE schemaname = 'public'
             AND indexname = 'ux_wms_import_rows_job_sheet_row'
       ) THEN 0 ELSE 1 END AS count_value;
