DO $migration$
BEGIN
    IF to_regclass('public.product_skus') IS NOT NULL THEN
        ALTER TABLE public.product_skus
            ADD COLUMN IF NOT EXISTS unit_weight_kg numeric(14, 6),
            ADD COLUMN IF NOT EXISTS unit_volume_m3 numeric(14, 6);
    END IF;
END
$migration$;
