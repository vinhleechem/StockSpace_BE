-- Read-only preflight for 20260825_06_finalize_rental_contract_refactor.sql.
-- Run with ON_ERROR_STOP enabled against a production copy. Every check named
-- invalid/overlap/missing is expected to return zero rows (or count_value = 0).

SELECT 'direct_relation_invalid' AS check_name, COUNT(*) AS count_value
FROM rental_contracts c
LEFT JOIN users owner_user ON owner_user.id = c.owner_id
LEFT JOIN users tenant_user ON tenant_user.id = c.tenant_id
LEFT JOIN warehouses w ON w.id = c.warehouse_id
WHERE c.owner_id IS NULL OR owner_user.id IS NULL
   OR c.tenant_id IS NULL OR tenant_user.id IS NULL
   OR c.warehouse_id IS NULL OR w.id IS NULL;

SELECT 'contract_terms_invalid' AS check_name, COUNT(*) AS count_value
FROM rental_contracts c
WHERE c.status IS NULL
   OR c.start_date IS NULL
   OR c.end_date IS NULL
   OR c.start_date > c.end_date
   OR c.pricing_type IS NULL
   OR c.pricing_type NOT IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY', 'NEGOTIATED')
   OR c.final_monthly_rent IS NULL OR c.final_monthly_rent <= 0
   OR c.leased_width IS NULL OR c.leased_width <= 0
   OR c.leased_length IS NULL OR c.leased_length <= 0
   OR c.leased_height IS NULL OR c.leased_height <= 0
   OR c.leased_area_m2 IS NULL OR c.leased_area_m2 <= 0
   OR c.leased_area_m2 <> c.leased_width * c.leased_length
   OR c.layout_snapshot IS NULL
   OR NOT pg_input_is_valid(c.layout_snapshot, 'jsonb')
   OR (c.paper_contract_files IS NOT NULL
       AND NOT pg_input_is_valid(c.paper_contract_files, 'jsonb'))
   OR (c.pricing_type = 'NEGOTIATED'
       AND (c.rental_price_snapshot IS NOT NULL OR c.final_monthly_rent <= 0))
   OR (c.pricing_type = 'FIXED_MONTHLY'
       AND (c.rental_price_snapshot IS NULL OR c.rental_price_snapshot <= 0
            OR c.final_monthly_rent <> c.rental_price_snapshot))
   OR (c.pricing_type = 'PER_SQUARE_METER_MONTHLY'
       AND (c.rental_price_snapshot IS NULL OR c.rental_price_snapshot <= 0
            OR c.final_monthly_rent <> c.rental_price_snapshot * c.leased_area_m2));

SELECT c1.id AS first_contract_id,
       c2.id AS second_contract_id,
       c1.tenant_id,
       c1.warehouse_id,
       c1.start_date AS first_start_date,
       c1.end_date AS first_end_date,
       c2.start_date AS second_start_date,
       c2.end_date AS second_end_date
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
  AND c2.is_active = TRUE AND c2.is_deleted = FALSE;

SELECT status, COUNT(*) AS count_value
FROM rental_contracts
WHERE status IN (
    'UNDER_NEGOTIATION', 'PENDING_TERMINATION', 'PENDING_CANCEL',
    'CANCELLED', 'PENDING_HANDOVER', 'COMPLETED', 'DISPUTED'
)
GROUP BY status
ORDER BY status;

SELECT 'warehouse_rented_remaining' AS check_name, COUNT(*) AS count_value
FROM warehouses
WHERE status = 'RENTED';

SELECT c.id AS active_contract_missing_tenant_layout,
       c.tenant_id,
       c.warehouse_id
FROM rental_contracts c
WHERE c.status = 'ACTIVE'
  AND c.is_active = TRUE
  AND c.is_deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM warehouse_layouts l
      WHERE l.tenant_id = c.tenant_id
        AND l.warehouse_id = c.warehouse_id
        AND l.is_default = FALSE
        AND l.is_active = TRUE
        AND l.is_deleted = FALSE
  );

SELECT 'warehouse_pricing_invalid' AS check_name, COUNT(*) AS count_value
FROM warehouses w
WHERE w.rental_pricing_type IS NULL
   OR w.rental_pricing_type NOT IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY', 'NEGOTIATED')
   OR (w.rental_pricing_type = 'NEGOTIATED' AND w.rental_price IS NOT NULL)
   OR (w.rental_pricing_type IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY')
       AND (w.rental_price IS NULL OR w.rental_price <= 0));
