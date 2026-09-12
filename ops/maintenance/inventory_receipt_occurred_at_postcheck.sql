-- Read-only post-deployment verification for receipt occurrence time.

SELECT 'missing_occurred_at_column' AS check_name,
       CASE WHEN EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'inventory_receipts'
             AND column_name = 'occurred_at'
             AND is_nullable = 'NO'
       ) THEN 0 ELSE 1 END AS count_value;

SELECT 'null_occurred_at_rows' AS check_name, COUNT(*) AS count_value
FROM public.inventory_receipts
WHERE occurred_at IS NULL;

SELECT 'missing_occurred_at_default' AS check_name,
       CASE WHEN column_default LIKE 'CURRENT_TIMESTAMP%'
            THEN 0 ELSE 1 END AS count_value
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'inventory_receipts'
  AND column_name = 'occurred_at';
