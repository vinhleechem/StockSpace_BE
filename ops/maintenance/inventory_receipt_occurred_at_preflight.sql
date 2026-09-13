-- Read-only preflight for 20260912_02_add_inventory_receipt_occurred_at.sql.
-- The migration is additive and backfills missing values from created_at.

SELECT 'missing_receipts_table' AS check_name,
       CASE WHEN to_regclass('public.inventory_receipts') IS NULL THEN 1 ELSE 0 END AS count_value;

-- Do not reference occurred_at here: this preflight must also run before the
-- column exists. The postcheck verifies the backfill and NOT NULL constraint.
SELECT 'occurred_at_already_exists' AS check_name,
       CASE WHEN EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'inventory_receipts'
             AND column_name = 'occurred_at'
       ) THEN 1 ELSE 0 END AS count_value;
