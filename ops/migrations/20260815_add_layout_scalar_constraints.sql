DO $migration$
BEGIN
    IF to_regclass('public.warehouse_layouts') IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'ck_warehouse_layouts_positive_dimensions'
        ) THEN
            ALTER TABLE public.warehouse_layouts
                ADD CONSTRAINT ck_warehouse_layouts_positive_dimensions
                CHECK (width IS NULL OR (width > 0 AND length > 0 AND height > 0)) NOT VALID;
        END IF;
    END IF;

    IF to_regclass('public.warehouse_racks') IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'ck_warehouse_racks_valid_geometry'
        ) THEN
            ALTER TABLE public.warehouse_racks
                ADD CONSTRAINT ck_warehouse_racks_valid_geometry
                CHECK (
                    (coordinate_x IS NULL OR coordinate_x >= 0)
                    AND (coordinate_y IS NULL OR coordinate_y >= 0)
                    AND (position_z IS NULL OR position_z >= 0)
                    AND (width IS NULL OR width > 0)
                    AND (length IS NULL OR length > 0)
                    AND (height IS NULL OR height > 0)
                    AND (max_weight IS NULL OR max_weight >= 0)
                    AND (max_volume IS NULL OR max_volume >= 0)
                ) NOT VALID;
        END IF;
    END IF;

    IF to_regclass('public.warehouse_bins') IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conname = 'ck_warehouse_bins_valid_geometry'
        ) THEN
            ALTER TABLE public.warehouse_bins
                ADD CONSTRAINT ck_warehouse_bins_valid_geometry
                CHECK (
                    (coordinate_x IS NULL OR coordinate_x >= 0)
                    AND (coordinate_y IS NULL OR coordinate_y >= 0)
                    AND (position_z IS NULL OR position_z >= 0)
                    AND (width IS NULL OR width > 0)
                    AND (length IS NULL OR length > 0)
                    AND (height IS NULL OR height > 0)
                    AND (max_weight IS NULL OR max_weight >= 0)
                    AND (max_volume IS NULL OR max_volume >= 0)
                ) NOT VALID;
        END IF;
    END IF;
END
$migration$;
