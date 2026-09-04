-- Warehouse creation now starts in DRAFT so the owner can finish the default
-- layout before submitting the warehouse for content approval.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'warehouses'
    ) THEN
        ALTER TABLE warehouses
            DROP CONSTRAINT IF EXISTS warehouses_status_check;
        ALTER TABLE warehouses
            ADD CONSTRAINT warehouses_status_check
            CHECK (status IN ('DRAFT', 'AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE'));
    END IF;
END $$;

-- Post-check (expected: zero):
-- SELECT COUNT(*) FROM warehouses
-- WHERE status NOT IN ('DRAFT', 'AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE');
