-- Read-only preflight for 20260912_01_add_wms_import_jobs.sql.
-- Every returned row requires review before deployment.

SELECT 'missing_required_table' AS check_name, required_table
FROM (VALUES
    ('users'),
    ('warehouses'),
    ('inventory_audits')
) AS required(required_table)
WHERE to_regclass('public.' || required_table) IS NULL;

SELECT 'object_name_collision' AS check_name, object_name, object_type
FROM (
    SELECT c.relname AS object_name,
           CASE c.relkind
               WHEN 'r' THEN 'table'
               WHEN 'i' THEN 'index'
               WHEN 'S' THEN 'sequence'
               ELSE c.relkind::text
           END AS object_type
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relname IN (
          'wms_import_jobs', 'wms_import_rows',
          'ux_wms_import_jobs_applied_content',
          'ux_wms_import_rows_job_sheet_row'
      )
) existing_objects
ORDER BY object_type, object_name;

-- The orphan-row query belongs to the postcheck because these tables may not
-- exist yet when this preflight is executed.
