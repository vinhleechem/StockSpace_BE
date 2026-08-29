-- Retire dispute and legacy handover permissions without deleting historical dispute rows.

BEGIN;

DELETE FROM role_permissions rp
USING permissions p
WHERE rp.permission_id = p.id
  AND p.name IN (
      'CONTRACT_HANDOVER_CONFIRM',
      'DISPUTE_CREATE',
      'DISPUTE_READ',
      'DISPUTE_RESOLVE'
  );

UPDATE permissions
SET is_active = FALSE,
    is_deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE name IN (
    'CONTRACT_HANDOVER_CONFIRM',
    'DISPUTE_CREATE',
    'DISPUTE_READ',
    'DISPUTE_RESOLVE'
)
  AND (is_active = TRUE OR is_deleted = FALSE);

UPDATE system_knowledge
SET is_active = FALSE,
    is_deleted = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE source_id = 'kb.damage-dispute.current'
  AND (is_active = TRUE OR is_deleted = FALSE);

COMMENT ON TABLE dispute_tickets IS
    'Historical dispute records retained after the rental dispute API was retired on 2026-08-25';

COMMIT;

-- Post-check (expected: zero rows):
-- SELECT 1 FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
-- WHERE p.name IN ('CONTRACT_HANDOVER_CONFIRM', 'DISPUTE_CREATE', 'DISPUTE_READ', 'DISPUTE_RESOLVE');
