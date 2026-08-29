-- A2: add direct Contract relations and immutable rental/layout snapshots.
-- All new columns remain nullable until the final compatibility cleanup.

ALTER TABLE rental_contracts
    ADD COLUMN IF NOT EXISTS owner_id UUID,
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS warehouse_id UUID,
    ADD COLUMN IF NOT EXISTS pricing_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS rental_price_snapshot NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS final_monthly_rent NUMERIC(15, 2),
    ADD COLUMN IF NOT EXISTS leased_width NUMERIC(15, 6),
    ADD COLUMN IF NOT EXISTS leased_length NUMERIC(15, 6),
    ADD COLUMN IF NOT EXISTS leased_height NUMERIC(15, 6),
    ADD COLUMN IF NOT EXISTS leased_area_m2 NUMERIC(15, 6),
    ADD COLUMN IF NOT EXISTS layout_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS change_request_reason TEXT,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'rental_contracts'
          AND column_name = 'paper_contract_images'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'rental_contracts'
          AND column_name = 'paper_contract_files'
    ) THEN
        ALTER TABLE rental_contracts
            RENAME COLUMN paper_contract_images TO paper_contract_files;
    END IF;
END $$;

ALTER TABLE rental_contracts
    ADD COLUMN IF NOT EXISTS paper_contract_files TEXT;

ALTER TABLE rental_contracts
    ALTER COLUMN booking_id DROP NOT NULL;

UPDATE rental_contracts c
SET owner_id = b_owner.owner_id,
    tenant_id = b_owner.tenant_id,
    warehouse_id = b_owner.warehouse_id
FROM (
    SELECT b.id AS booking_id,
           w.owner_id,
           b.tenant_id,
           b.warehouse_id
    FROM booking_requests b
    JOIN warehouses w ON w.id = b.warehouse_id
) b_owner
WHERE c.booking_id = b_owner.booking_id
  AND (c.owner_id IS NULL OR c.tenant_id IS NULL OR c.warehouse_id IS NULL);

UPDATE rental_contracts c
SET pricing_type = COALESCE(w.rental_pricing_type, 'FIXED_MONTHLY'),
    rental_price_snapshot = COALESCE(w.rental_price, w.price_per_month),
    final_monthly_rent = CASE
        WHEN COALESCE(w.rental_pricing_type, 'FIXED_MONTHLY')
                 IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY')
            THEN COALESCE(w.rental_price, w.price_per_month)
        ELSE c.final_monthly_rent
    END
FROM booking_requests b
JOIN warehouses w ON w.id = b.warehouse_id
WHERE c.booking_id = b.id
  AND c.pricing_type IS NULL;

-- Prefer an active tenant layout; fall back to the warehouse default layout.
-- If both are missing, dimensions remain NULL for preflight instead of being
-- invented by the migration.
UPDATE rental_contracts c
SET leased_width = COALESCE(tenant_layout.width, default_layout.width),
    leased_length = COALESCE(tenant_layout.length, default_layout.length),
    leased_height = COALESCE(tenant_layout.height, default_layout.height),
    leased_area_m2 = CASE
        WHEN COALESCE(tenant_layout.width, default_layout.width) IS NOT NULL
         AND COALESCE(tenant_layout.length, default_layout.length) IS NOT NULL
            THEN COALESCE(tenant_layout.width, default_layout.width)
               * COALESCE(tenant_layout.length, default_layout.length)
        ELSE NULL
    END
FROM booking_requests b
LEFT JOIN LATERAL (
    SELECT l.width, l.length, l.height
    FROM warehouse_layouts l
    WHERE l.warehouse_id = b.warehouse_id
      AND l.tenant_id = b.tenant_id
      AND l.is_active = true
      AND l.is_deleted = false
    ORDER BY l.updated_at DESC NULLS LAST
    LIMIT 1
) tenant_layout ON true
LEFT JOIN LATERAL (
    SELECT l.width, l.length, l.height
    FROM warehouse_layouts l
    WHERE l.warehouse_id = b.warehouse_id
      AND l.is_default = true
      AND l.is_active = true
      AND l.is_deleted = false
    ORDER BY l.updated_at DESC NULLS LAST
    LIMIT 1
) default_layout ON true
WHERE c.booking_id = b.id
  AND c.leased_width IS NULL;

-- Store a valid JSON object for legacy contracts when a source layout exists.
-- positions is kept as a JSON string for legacy rows because the migration
-- must not guess whether malformed historical text represents an array.
UPDATE rental_contracts c
SET layout_snapshot = jsonb_build_object(
        'layoutId', source_layout.id,
        'warehouseId', b.warehouse_id,
        'tenantId', b.tenant_id,
        'isDefault', source_layout.is_default,
        'width', source_layout.width,
        'length', source_layout.length,
        'height', source_layout.height,
        'positions', COALESCE(source_layout.positions, '[]')
    )::text
FROM booking_requests b
LEFT JOIN LATERAL (
    SELECT l.id, l.is_default, l.width, l.length, l.height, l.positions
    FROM warehouse_layouts l
    WHERE l.warehouse_id = b.warehouse_id
      AND (
          (l.tenant_id = b.tenant_id AND l.is_active = true AND l.is_deleted = false)
          OR (l.is_default = true AND l.is_active = true AND l.is_deleted = false)
      )
    ORDER BY CASE WHEN l.tenant_id = b.tenant_id THEN 0 ELSE 1 END,
             l.updated_at DESC NULLS LAST
    LIMIT 1
) source_layout ON true
WHERE c.booking_id = b.id
  AND c.layout_snapshot IS NULL
  AND source_layout.id IS NOT NULL;

-- Historical code used List.toString() for this field. Preserve the value,
-- but make malformed legacy text valid JSON without pretending it is an array.
UPDATE rental_contracts
SET paper_contract_files = to_jsonb(paper_contract_files)::text
WHERE paper_contract_files IS NOT NULL
  AND NOT pg_input_is_valid(paper_contract_files, 'jsonb');

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_rental_contracts_owner'
    ) THEN
        ALTER TABLE rental_contracts
            ADD CONSTRAINT fk_rental_contracts_owner
            FOREIGN KEY (owner_id) REFERENCES users(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_rental_contracts_tenant'
    ) THEN
        ALTER TABLE rental_contracts
            ADD CONSTRAINT fk_rental_contracts_tenant
            FOREIGN KEY (tenant_id) REFERENCES users(id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_rental_contracts_warehouse'
    ) THEN
        ALTER TABLE rental_contracts
            ADD CONSTRAINT fk_rental_contracts_warehouse
            FOREIGN KEY (warehouse_id) REFERENCES warehouses(id);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_rental_contracts_pricing_type'
    ) THEN
        ALTER TABLE rental_contracts
            ADD CONSTRAINT ck_rental_contracts_pricing_type
            CHECK (pricing_type IS NULL OR pricing_type IN (
                'FIXED_MONTHLY',
                'PER_SQUARE_METER_MONTHLY',
                'NEGOTIATED'
            )) NOT VALID;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'rental_contracts') THEN
        ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_status_check;
        ALTER TABLE rental_contracts ADD CONSTRAINT rental_contracts_status_check
        CHECK (status IN (
            'DRAFT', 'UNDER_NEGOTIATION', 'PENDING_TENANT_CONFIRM',
            'CHANGES_REQUESTED', 'ACTIVE', 'REJECTED', 'EXPIRED',
            'PENDING_TERMINATION', 'PENDING_CANCEL', 'CANCELLED',
            'PENDING_HANDOVER', 'COMPLETED', 'DISPUTED'
        )) NOT VALID;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_rental_contracts_owner_id
    ON rental_contracts (owner_id);

CREATE INDEX IF NOT EXISTS idx_rental_contracts_tenant_id
    ON rental_contracts (tenant_id);

CREATE INDEX IF NOT EXISTS idx_rental_contracts_warehouse_id
    ON rental_contracts (warehouse_id);

CREATE INDEX IF NOT EXISTS idx_rental_contracts_tenant_warehouse_status
    ON rental_contracts (tenant_id, warehouse_id, status);
