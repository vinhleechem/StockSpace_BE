
DO $migration$
BEGIN
    IF to_regclass('public.warehouse_layouts') IS NOT NULL THEN
        ALTER TABLE public.warehouse_layouts
            ADD COLUMN IF NOT EXISTS length integer;

        UPDATE public.warehouse_layouts
        SET length = 100
        WHERE length IS NULL;

        ALTER TABLE public.warehouse_layouts
            ALTER COLUMN length SET NOT NULL;
    END IF;
END
$migration$;
