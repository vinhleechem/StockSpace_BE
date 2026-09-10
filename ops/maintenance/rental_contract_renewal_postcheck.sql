-- Read-only post-deployment verification. Expected count_value is zero.

SELECT 'unsupported_contract_status' AS check_name, COUNT(*) AS count_value
FROM public.rental_contracts
WHERE status NOT IN (
    'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
    'SCHEDULED', 'ACTIVE', 'REJECTED', 'EXPIRED'
);

SELECT 'orphan_or_self_renewal' AS check_name, COUNT(*) AS count_value
FROM public.rental_contracts c
LEFT JOIN public.rental_contracts source
       ON source.id = c.renewed_from_contract_id
WHERE c.renewed_from_contract_id IS NOT NULL
  AND (source.id IS NULL OR source.id = c.id);

SELECT 'duplicate_blocking_successor' AS check_name, COUNT(*) AS count_value
FROM (
    SELECT renewed_from_contract_id
    FROM public.rental_contracts
    WHERE renewed_from_contract_id IS NOT NULL
      AND status IN (
          'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
          'SCHEDULED', 'ACTIVE', 'EXPIRED'
      )
      AND is_active = TRUE
      AND is_deleted = FALSE
    GROUP BY renewed_from_contract_id
    HAVING COUNT(*) > 1
) duplicates;

SELECT 'missing_renewal_index' AS check_name,
       CASE WHEN EXISTS (
           SELECT 1
           FROM pg_indexes
           WHERE schemaname = 'public'
             AND indexname = 'uq_rental_contracts_blocking_renewal_source'
       ) THEN 0 ELSE 1 END AS count_value;

SELECT 'missing_renewal_foreign_key' AS check_name,
       CASE WHEN EXISTS (
           SELECT 1
           FROM pg_constraint
           WHERE conrelid = 'public.rental_contracts'::regclass
             AND conname = 'fk_rental_contracts_renewed_from'
       ) THEN 0 ELSE 1 END AS count_value;
