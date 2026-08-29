-- Post-migration verification. All count_value results must be zero.

SELECT 'invalid_contract_status' AS check_name, COUNT(*) AS count_value
FROM rental_contracts
WHERE status NOT IN (
    'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
    'ACTIVE', 'REJECTED', 'EXPIRED'
);

SELECT 'invalid_direct_contract_terms' AS check_name, COUNT(*) AS count_value
FROM rental_contracts
WHERE owner_id IS NULL OR tenant_id IS NULL OR warehouse_id IS NULL
   OR start_date IS NULL OR end_date IS NULL OR start_date > end_date
   OR pricing_type IS NULL OR final_monthly_rent IS NULL OR final_monthly_rent <= 0
   OR leased_width IS NULL OR leased_width <= 0
   OR leased_length IS NULL OR leased_length <= 0
   OR leased_height IS NULL OR leased_height <= 0
   OR leased_area_m2 IS NULL OR leased_area_m2 <> leased_width * leased_length
   OR layout_snapshot IS NULL OR NOT pg_input_is_valid(layout_snapshot, 'jsonb')
   OR (pricing_type = 'NEGOTIATED' AND rental_price_snapshot IS NOT NULL)
   OR (pricing_type = 'FIXED_MONTHLY'
       AND (rental_price_snapshot IS NULL OR rental_price_snapshot <= 0
            OR final_monthly_rent <> rental_price_snapshot))
   OR (pricing_type = 'PER_SQUARE_METER_MONTHLY'
       AND (rental_price_snapshot IS NULL OR rental_price_snapshot <= 0
            OR final_monthly_rent <> rental_price_snapshot * leased_area_m2));

SELECT 'active_or_pending_overlap' AS check_name, COUNT(*) AS count_value
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

SELECT 'invalid_warehouse_status_or_pricing' AS check_name, COUNT(*) AS count_value
FROM warehouses
WHERE status NOT IN ('AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE')
   OR rental_pricing_type NOT IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY', 'NEGOTIATED')
   OR (rental_pricing_type = 'NEGOTIATED' AND rental_price IS NOT NULL)
   OR (rental_pricing_type IN ('FIXED_MONTHLY', 'PER_SQUARE_METER_MONTHLY')
       AND (rental_price IS NULL OR rental_price <= 0));

SELECT column_name AS unexpected_legacy_column
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND (
      (table_name = 'rental_contracts' AND column_name IN (
          'booking_id', 'tenant_confirmed', 'owner_confirmed', 'cancel_reason',
          'cancel_evidence', 'tenant_termination', 'deposit_forfeited', 'paper_contract_images'
      ))
      OR (table_name = 'warehouses' AND column_name = 'price_per_month')
  )
ORDER BY table_name, column_name;
