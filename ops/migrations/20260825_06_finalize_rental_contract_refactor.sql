-- Finalize direct rental contracts after the preflight returns no structural
-- invalid rows or active/pending date overlaps. Historical Booking, Dispute,
-- and Transaction tables remain intact and independent.

BEGIN;

-- Map legacy states before narrowing the status constraint. Preserve the old
-- state in a deterministic rejection reason when no human-entered reason exists.
UPDATE rental_contracts
SET status = 'EXPIRED'
WHERE status IN ('COMPLETED', 'PENDING_HANDOVER');

UPDATE rental_contracts
SET rejection_reason = COALESCE(
        NULLIF(BTRIM(rejection_reason), ''),
        'Migrated from retired contract state ' || status || ' on 2026-08-25'
    ),
    status = 'REJECTED'
WHERE status IN (
    'UNDER_NEGOTIATION', 'PENDING_TERMINATION', 'PENDING_CANCEL',
    'CANCELLED', 'DISPUTED'
);

-- A submitted legacy contract that cannot be safely presented to the tenant
-- is rejected instead of remaining actionable. Structural gaps are still
-- rejected by the guard below and must be remediated before this migration.
UPDATE rental_contracts
SET rejection_reason = COALESCE(
        NULLIF(BTRIM(rejection_reason), ''),
        'Rejected during direct-contract migration because submitted files or terms were invalid'
    ),
    status = 'REJECTED'
WHERE status = 'PENDING_TENANT_CONFIRM'
  AND (
      paper_contract_files IS NULL
      OR NOT pg_input_is_valid(paper_contract_files, 'jsonb')
      OR pricing_type NOT IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY', 'NEGOTIATED')
      OR final_monthly_rent IS NULL OR final_monthly_rent <= 0
      OR layout_snapshot IS NULL OR NOT pg_input_is_valid(layout_snapshot, 'jsonb')
  );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM rental_contracts c
        LEFT JOIN users owner_user ON owner_user.id = c.owner_id
        LEFT JOIN users tenant_user ON tenant_user.id = c.tenant_id
        LEFT JOIN warehouses w ON w.id = c.warehouse_id
        WHERE c.owner_id IS NULL OR owner_user.id IS NULL
           OR c.tenant_id IS NULL OR tenant_user.id IS NULL
           OR c.warehouse_id IS NULL OR w.id IS NULL
           OR c.status IS NULL
           OR c.start_date IS NULL OR c.end_date IS NULL OR c.start_date > c.end_date
           OR c.pricing_type IS NULL
           OR c.pricing_type NOT IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY', 'NEGOTIATED')
           OR c.final_monthly_rent IS NULL OR c.final_monthly_rent <= 0
           OR c.leased_width IS NULL OR c.leased_width <= 0
           OR c.leased_length IS NULL OR c.leased_length <= 0
           OR c.leased_height IS NULL OR c.leased_height <= 0
           OR c.leased_area_m2 IS NULL OR c.leased_area_m2 <= 0
           OR c.leased_area_m2 <> c.leased_width * c.leased_length
           OR c.layout_snapshot IS NULL OR NOT pg_input_is_valid(c.layout_snapshot, 'jsonb')
           OR (c.paper_contract_files IS NOT NULL
               AND NOT pg_input_is_valid(c.paper_contract_files, 'jsonb'))
           OR (c.pricing_type = 'NEGOTIATED' AND c.rental_price_snapshot IS NOT NULL)
           OR (c.pricing_type = 'FIXED_MONTHLY'
               AND (c.rental_price_snapshot IS NULL OR c.rental_price_snapshot <= 0
                    OR c.final_monthly_rent <> c.rental_price_snapshot))
           OR (c.pricing_type = 'PER_SQUARE_METER_MONTHLY'
               AND (c.rental_price_snapshot IS NULL OR c.rental_price_snapshot <= 0
                    OR c.final_monthly_rent <> c.rental_price_snapshot * c.leased_area_m2))
    ) THEN
        RAISE EXCEPTION
            'Rental contract finalization aborted: direct relations or immutable terms are invalid; run the preflight';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM rental_contracts c1
        JOIN rental_contracts c2
          ON c1.id < c2.id
         AND c1.tenant_id = c2.tenant_id
         AND c1.warehouse_id = c2.warehouse_id
         AND c1.start_date <= c2.end_date
         AND c1.end_date >= c2.start_date
        WHERE c1.status IN ('PENDING_TENANT_CONFIRM', 'ACTIVE')
          AND c2.status IN ('PENDING_TENANT_CONFIRM', 'ACTIVE')
          AND c1.is_active = TRUE AND c1.is_deleted = FALSE
          AND c2.is_active = TRUE AND c2.is_deleted = FALSE
    ) THEN
        RAISE EXCEPTION
            'Rental contract finalization aborted: overlapping active/actionable contracts exist';
    END IF;
END $$;

ALTER TABLE rental_contracts
    ALTER COLUMN owner_id SET NOT NULL,
    ALTER COLUMN tenant_id SET NOT NULL,
    ALTER COLUMN warehouse_id SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN end_date SET NOT NULL,
    ALTER COLUMN pricing_type SET NOT NULL,
    ALTER COLUMN final_monthly_rent SET NOT NULL,
    ALTER COLUMN leased_width SET NOT NULL,
    ALTER COLUMN leased_length SET NOT NULL,
    ALTER COLUMN leased_height SET NOT NULL,
    ALTER COLUMN leased_area_m2 SET NOT NULL,
    ALTER COLUMN layout_snapshot SET NOT NULL;

ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_status_check;
ALTER TABLE rental_contracts
    ADD CONSTRAINT rental_contracts_status_check
    CHECK (status IN (
        'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
        'ACTIVE', 'REJECTED', 'EXPIRED'
    ));

ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS ck_rental_contracts_pricing_type;
ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS ck_rental_contracts_terms;
ALTER TABLE rental_contracts
    ADD CONSTRAINT ck_rental_contracts_terms
    CHECK (
        start_date <= end_date
        AND leased_width > 0
        AND leased_length > 0
        AND leased_height > 0
        AND leased_area_m2 = leased_width * leased_length
        AND final_monthly_rent > 0
        AND (
            (pricing_type = 'NEGOTIATED'
             AND rental_price_snapshot IS NULL)
            OR
            (pricing_type = 'FIXED_MONTHLY'
             AND rental_price_snapshot IS NOT NULL
             AND rental_price_snapshot > 0
             AND final_monthly_rent = rental_price_snapshot)
            OR
            (pricing_type = 'PER_SQUARE_METER_MONTHLY'
             AND rental_price_snapshot IS NOT NULL
             AND rental_price_snapshot > 0
             AND final_monthly_rent = rental_price_snapshot * leased_area_m2)
        )
    );

ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS ck_warehouses_rental_pricing_type;
ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS ck_warehouses_rental_price_by_type;
ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS ck_warehouses_rental_pricing;
ALTER TABLE warehouses
    ADD CONSTRAINT ck_warehouses_rental_pricing
    CHECK (
        (rental_pricing_type = 'NEGOTIATED' AND rental_price IS NULL)
        OR
        (rental_pricing_type IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY')
         AND rental_price IS NOT NULL
         AND rental_price > 0)
    );

CREATE INDEX IF NOT EXISTS idx_rental_contracts_status_end_date
    ON rental_contracts (status, end_date)
    WHERE is_active = TRUE AND is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_rental_contracts_actionable_period
    ON rental_contracts (tenant_id, warehouse_id, start_date, end_date)
    WHERE status IN ('PENDING_TENANT_CONFIRM', 'ACTIVE')
      AND is_active = TRUE AND is_deleted = FALSE;

ALTER TABLE rental_contracts
    DROP COLUMN IF EXISTS booking_id,
    DROP COLUMN IF EXISTS tenant_confirmed,
    DROP COLUMN IF EXISTS owner_confirmed,
    DROP COLUMN IF EXISTS cancel_reason,
    DROP COLUMN IF EXISTS cancel_evidence,
    DROP COLUMN IF EXISTS tenant_termination,
    DROP COLUMN IF EXISTS deposit_forfeited,
    DROP COLUMN IF EXISTS paper_contract_images;

ALTER TABLE warehouses
    DROP COLUMN IF EXISTS price_per_month;

COMMIT;
