-- A10 corrective cleanup: retire the legacy flat posting-fee configuration
-- after publication periods became the only warehouse listing flow.
-- Historical transactions are intentionally preserved.

UPDATE system_configs
SET is_active = false,
    is_deleted = true,
    updated_at = CURRENT_TIMESTAMP
WHERE config_key IN ('warehouse_publish_fee', 'warehouse_publish_package_id');

-- Do not hard-delete a service package that may be referenced by a historical
-- subscription. Unreferenced legacy POSTING_FEE packages are only retired
-- from active catalog views.
UPDATE service_packages sp
SET is_active = false,
    is_deleted = true,
    updated_at = CURRENT_TIMESTAMP
WHERE sp.features LIKE '%POSTING_FEE%'
  AND NOT EXISTS (
      SELECT 1
      FROM subscriptions s
      WHERE s.package_id = sp.id
  );
