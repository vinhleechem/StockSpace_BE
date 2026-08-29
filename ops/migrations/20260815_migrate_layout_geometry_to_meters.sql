DO $migration$
BEGIN
    IF to_regclass('public.warehouse_layouts') IS NOT NULL THEN
        ALTER TABLE public.warehouse_layouts
            ALTER COLUMN width TYPE numeric(14, 6) USING width::numeric,
            ALTER COLUMN length TYPE numeric(14, 6) USING length::numeric,
            ALTER COLUMN height TYPE numeric(14, 6) USING height::numeric;
    END IF;

    IF to_regclass('public.warehouse_racks') IS NOT NULL THEN
        ALTER TABLE public.warehouse_racks
            ALTER COLUMN coordinate_x TYPE numeric(14, 6) USING coordinate_x::numeric,
            ALTER COLUMN coordinate_y TYPE numeric(14, 6) USING coordinate_y::numeric,
            ALTER COLUMN position_z TYPE numeric(14, 6) USING position_z::numeric,
            ALTER COLUMN width TYPE numeric(14, 6) USING width::numeric,
            ALTER COLUMN length TYPE numeric(14, 6) USING length::numeric,
            ALTER COLUMN height TYPE numeric(14, 6) USING height::numeric,
            ALTER COLUMN max_weight TYPE numeric(14, 6) USING max_weight::numeric,
            ALTER COLUMN max_volume TYPE numeric(14, 6) USING max_volume::numeric;
    END IF;

    IF to_regclass('public.warehouse_bins') IS NOT NULL THEN
        ALTER TABLE public.warehouse_bins
            ALTER COLUMN coordinate_x TYPE numeric(14, 6) USING coordinate_x::numeric,
            ALTER COLUMN coordinate_y TYPE numeric(14, 6) USING coordinate_y::numeric,
            ALTER COLUMN position_z TYPE numeric(14, 6) USING position_z::numeric,
            ALTER COLUMN width TYPE numeric(14, 6) USING width::numeric,
            ALTER COLUMN length TYPE numeric(14, 6) USING length::numeric,
            ALTER COLUMN height TYPE numeric(14, 6) USING height::numeric,
            ALTER COLUMN max_weight TYPE numeric(14, 6) USING max_weight::numeric,
            ALTER COLUMN max_volume TYPE numeric(14, 6) USING max_volume::numeric;
    END IF;
END
$migration$;
