-- Repair legacy layout rows before enforcing the non-null constraint declared
-- by WarehouseLayout.length. The value 100 matches the entity's default.
-- Safe to run repeatedly and on a fresh database.

DO $migration$
BEGIN
    IF to_regclass('public.warehouse_layouts') IS NOT NULL THEN
        UPDATE public.warehouse_layouts
        SET length = 100
        WHERE length IS NULL;

        ALTER TABLE public.warehouse_layouts
            ALTER COLUMN length SET NOT NULL;
    END IF;
END
$migration$;
