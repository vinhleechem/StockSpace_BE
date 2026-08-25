-- Warehouse status describes listing lifecycle only; tenancy is represented by rental_contracts.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = current_schema()
          AND table_name = 'warehouses'
    ) THEN
        UPDATE warehouses
        SET status = 'AVAILABLE',
            updated_at = CURRENT_TIMESTAMP
        WHERE status = 'RENTED';

        ALTER TABLE warehouses
            DROP CONSTRAINT IF EXISTS warehouses_status_check;
        ALTER TABLE warehouses
            ADD CONSTRAINT warehouses_status_check
            CHECK (status IN ('AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE'));
    END IF;
END $$;

-- Post-check (expected: zero):
-- SELECT COUNT(*) FROM warehouses WHERE status NOT IN ('AVAILABLE', 'PENDING_APPROVAL', 'INACTIVE');
