-- Align the warehouse area projection with the active default layout.
-- The migration is intentionally additive and does not rewrite rental contracts.

DO $migration$
BEGIN
    IF to_regclass('public.warehouses') IS NULL
       OR to_regclass('public.warehouse_layouts') IS NULL
       OR to_regclass('public.rental_contracts') IS NULL THEN
        RAISE EXCEPTION
            'Rental area allocation migration requires warehouses, warehouse_layouts and rental_contracts';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.warehouse_layouts
        WHERE is_default = TRUE
          AND is_active = TRUE
          AND is_deleted = FALSE
          AND (
              width IS NULL OR length IS NULL OR height IS NULL
              OR width <= 0 OR length <= 0 OR height <= 0
          )
    ) THEN
        RAISE EXCEPTION
            'Rental area allocation migration stopped: invalid active default layout dimensions exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.warehouse_layouts
        WHERE is_default = TRUE
          AND is_active = TRUE
          AND is_deleted = FALSE
        GROUP BY warehouse_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Rental area allocation migration stopped: multiple active default layouts exist for a warehouse';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.warehouse_layouts
        WHERE is_default = TRUE
          AND is_active = TRUE
          AND is_deleted = FALSE
          AND ROUND(width * length, 2) > 99999999.99
    ) THEN
        RAISE EXCEPTION
            'Rental area allocation migration stopped: default layout area exceeds warehouses.capacity precision';
    END IF;
END
$migration$;

UPDATE public.warehouses w
SET capacity = ROUND(l.width * l.length, 2)
FROM public.warehouse_layouts l
WHERE l.warehouse_id = w.id
  AND l.is_default = TRUE
  AND l.is_active = TRUE
  AND l.is_deleted = FALSE
  AND l.width > 0
  AND l.length > 0
  AND l.height > 0;

CREATE INDEX IF NOT EXISTS idx_rental_contracts_warehouse_allocation_period
    ON public.rental_contracts (warehouse_id, status, start_date, end_date)
    WHERE is_active = TRUE AND is_deleted = FALSE;
