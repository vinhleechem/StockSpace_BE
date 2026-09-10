-- Read-only preflight for the contract renewal lifecycle migration.
-- Run before deployment. Every returned row requires review.

SELECT 'unsupported_contract_status' AS check_name, status, COUNT(*) AS row_count
FROM public.rental_contracts
GROUP BY status
HAVING status NOT IN (
    'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
    'SCHEDULED', 'ACTIVE', 'REJECTED', 'EXPIRED'
);

SELECT 'invalid_direct_contract_terms' AS check_name, COUNT(*) AS row_count
FROM public.rental_contracts
WHERE is_active = TRUE
  AND is_deleted = FALSE
  AND (
      owner_id IS NULL OR tenant_id IS NULL OR warehouse_id IS NULL
      OR start_date IS NULL OR end_date IS NULL OR start_date > end_date
      OR pricing_type IS NULL OR final_monthly_rent IS NULL OR final_monthly_rent <= 0
      OR leased_width IS NULL OR leased_width <= 0
      OR leased_length IS NULL OR leased_length <= 0
      OR leased_height IS NULL OR leased_height <= 0
      OR leased_area_m2 IS NULL OR leased_area_m2 <= 0
      OR leased_area_m2 <> leased_width * leased_length
      OR layout_snapshot IS NULL
  );

SELECT 'actionable_overlap' AS check_name,
       c1.id AS first_contract_id,
       c2.id AS second_contract_id
FROM public.rental_contracts c1
JOIN public.rental_contracts c2
  ON c1.id < c2.id
 AND c1.tenant_id = c2.tenant_id
 AND c1.warehouse_id = c2.warehouse_id
 AND c1.start_date <= c2.end_date
 AND c1.end_date >= c2.start_date
WHERE c1.status IN ('PENDING_TENANT_CONFIRM', 'SCHEDULED', 'ACTIVE')
  AND c2.status IN ('PENDING_TENANT_CONFIRM', 'SCHEDULED', 'ACTIVE')
  AND c1.is_active = TRUE AND c1.is_deleted = FALSE
  AND c2.is_active = TRUE AND c2.is_deleted = FALSE;

-- Use to_jsonb(row) so this preflight remains executable before the additive
-- column exists. If the column was created manually, the same query checks
-- orphan and self references without hard-coding a pre-migration schema.
WITH renewal_rows AS (
    SELECT c.id,
           NULLIF(to_jsonb(c)->>'renewed_from_contract_id', '')::uuid
               AS renewed_from_contract_id
    FROM public.rental_contracts c
    WHERE to_jsonb(c) ? 'renewed_from_contract_id'
)
SELECT 'renewal_column_state' AS check_name,
       r.id AS successor_id,
       r.renewed_from_contract_id,
       source.id AS source_id
FROM renewal_rows r
LEFT JOIN public.rental_contracts source
       ON source.id = r.renewed_from_contract_id
WHERE r.renewed_from_contract_id IS NOT NULL
  AND (source.id IS NULL OR r.id = r.renewed_from_contract_id);

WITH renewal_rows AS (
    SELECT NULLIF(to_jsonb(c)->>'renewed_from_contract_id', '')::uuid
               AS renewed_from_contract_id,
           c.status,
           c.is_active,
           c.is_deleted
    FROM public.rental_contracts c
    WHERE to_jsonb(c) ? 'renewed_from_contract_id'
)
SELECT 'duplicate_blocking_successor' AS check_name,
       renewed_from_contract_id,
       COUNT(*) AS row_count
FROM renewal_rows
WHERE renewed_from_contract_id IS NOT NULL
  AND status IN (
      'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
      'SCHEDULED', 'ACTIVE', 'EXPIRED'
  )
  AND is_active = TRUE
  AND is_deleted = FALSE
GROUP BY renewed_from_contract_id
HAVING COUNT(*) > 1;

SELECT 'name_collision' AS check_name, object_name, object_type
FROM (
    SELECT indexname AS object_name, 'index' AS object_type
    FROM pg_indexes
    WHERE schemaname = 'public'
      AND indexname IN (
          'idx_rental_contracts_renewed_from',
          'uq_rental_contracts_blocking_renewal_source'
      )
    UNION ALL
    SELECT conname AS object_name, 'constraint' AS object_type
    FROM pg_constraint
    WHERE conname IN (
        'fk_rental_contracts_renewed_from',
        'ck_rental_contracts_not_self_renewal',
        'rental_contracts_status_check'
    )
) existing_objects
ORDER BY object_type, object_name;
