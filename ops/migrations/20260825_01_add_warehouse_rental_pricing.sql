-- A1: make warehouse rental pricing explicit while keeping the legacy
-- price_per_month column during the frontend compatibility period.

ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS rental_pricing_type VARCHAR(40);

ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS rental_price NUMERIC(15, 2);

UPDATE warehouses
SET rental_pricing_type = 'FIXED_MONTHLY'
WHERE rental_pricing_type IS NULL;

UPDATE warehouses
SET rental_price = price_per_month
WHERE rental_price IS NULL;

-- The old column is retained for rollback/legacy inspection, but new writes
-- use rental_price. It must therefore no longer block inserts with NULL.
ALTER TABLE warehouses
    ALTER COLUMN price_per_month DROP NOT NULL;

ALTER TABLE warehouses
    ALTER COLUMN rental_pricing_type SET DEFAULT 'FIXED_MONTHLY';

ALTER TABLE warehouses
    ALTER COLUMN rental_pricing_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_warehouses_rental_pricing_type'
    ) THEN
        ALTER TABLE warehouses
            ADD CONSTRAINT ck_warehouses_rental_pricing_type
            CHECK (rental_pricing_type IN (
                'FIXED_MONTHLY',
                'PER_SQUARE_METER_MONTHLY',
                'NEGOTIATED'
            )) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_warehouses_rental_price_by_type'
    ) THEN
        ALTER TABLE warehouses
            ADD CONSTRAINT ck_warehouses_rental_price_by_type
            CHECK (
                (rental_pricing_type = 'NEGOTIATED' AND rental_price IS NULL)
                OR (
                    rental_pricing_type IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY')
                    AND rental_price IS NOT NULL
                    AND rental_price > 0
                )
            ) NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_warehouses_rental_price
    ON warehouses (rental_price);
