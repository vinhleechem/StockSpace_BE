-- S02: store searchable location components without parsing free-form address at runtime.

ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS province_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS province_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS district_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS district_name VARCHAR(255);

-- Existing StockSpace data created by the current HCM-only frontend has a
-- deterministic province suffix. District values are intentionally not guessed
-- from a free-form address and remain NULL until a structured payload is sent.
UPDATE warehouses
SET province_code = '79',
    province_name = 'Thành phố Hồ Chí Minh'
WHERE province_code IS NULL
  AND province_name IS NULL
  AND address ILIKE '%Thành phố Hồ Chí Minh';

CREATE INDEX IF NOT EXISTS idx_warehouses_province_code
    ON warehouses (province_code);

CREATE INDEX IF NOT EXISTS idx_warehouses_district_code
    ON warehouses (district_code);

CREATE INDEX IF NOT EXISTS idx_warehouses_province_district
    ON warehouses (province_code, district_code);
