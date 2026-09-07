-- Warehouse creation is submitted for Admin approval immediately. The
-- layout is completed in the following request, so warehouses no longer need
-- a persisted DRAFT status.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'warehouses'
    ) THEN
        -- Keep data created by the previous deployment compatible before
        -- narrowing the status constraint.
        UPDATE warehouses
        SET status = 'PENDING_APPROVAL',
            updated_at = CURRENT_TIMESTAMP
        WHERE status = 'DRAFT';

        ALTER TABLE warehouses
            DROP CONSTRAINT IF EXISTS warehouses_status_check;
        ALTER TABLE warehouses
            ADD CONSTRAINT warehouses_status_check
            CHECK (status IN ('AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE'));
    END IF;
END $$;

-- Post-check (expected: zero):
-- SELECT COUNT(*) FROM warehouses WHERE status = 'DRAFT';
