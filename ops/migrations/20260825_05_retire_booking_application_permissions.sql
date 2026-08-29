-- Retire rental-request application permissions and the former security-deposit setting.
-- Historical booking_requests and wallet transactions are deliberately retained.

BEGIN;

DELETE FROM role_permissions rp
USING permissions p
WHERE rp.permission_id = p.id
  AND p.name IN (
      'RENTAL_REQUEST_CREATE',
      'RENTAL_REQUEST_READ',
      'RENTAL_REQUEST_PROCESS'
  );

UPDATE permissions
SET is_active = FALSE,
    is_deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE name IN (
    'RENTAL_REQUEST_CREATE',
    'RENTAL_REQUEST_READ',
    'RENTAL_REQUEST_PROCESS'
)
  AND (is_active = TRUE OR is_deleted = FALSE);

UPDATE system_configs
SET is_active = FALSE,
    is_deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE config_key = 'deposit_percentage'
  AND (is_active = TRUE OR is_deleted = FALSE);

UPDATE system_knowledge
SET is_active = FALSE,
    is_deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id IN ('kb.deposit.current', 'kb.cancellation.current')
  AND (is_active = TRUE OR is_deleted = FALSE);

COMMIT;

-- Post-check (expected: zero rows for each query):
-- SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.name IN ('RENTAL_REQUEST_CREATE', 'RENTAL_REQUEST_READ', 'RENTAL_REQUEST_PROCESS');
-- SELECT 1 FROM system_configs WHERE config_key = 'deposit_percentage' AND is_deleted = FALSE;
-- SELECT 1 FROM system_knowledge WHERE source_id IN ('kb.deposit.current', 'kb.cancellation.current')
-- AND is_deleted = FALSE;
