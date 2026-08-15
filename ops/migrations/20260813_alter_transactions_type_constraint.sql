
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions') THEN
        ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_transaction_type_check;
        ALTER TABLE transactions ADD CONSTRAINT transactions_transaction_type_check
        CHECK (transaction_type IN ('TOP_UP', 'WITHDRAWAL', 'DEPOSIT_PAYMENT', 'DEPOSIT_RECEIVED', 'DEPOSIT_REFUND', 'PACKAGE_PAYMENT', 'COMMISSION'));
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'rental_contracts') THEN
        ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_status_check;
        ALTER TABLE rental_contracts ADD CONSTRAINT rental_contracts_status_check
        CHECK (status IN ('UNDER_NEGOTIATION', 'PENDING_TENANT_CONFIRM', 'ACTIVE', 'PENDING_CANCEL', 'CANCELLED', 'PENDING_HANDOVER', 'COMPLETED', 'DISPUTED'));
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'subscriptions') THEN
        ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_status_check;
        ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED', 'SUPERSEDED'));
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'warehouses') THEN
        ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS warehouses_status_check;
        ALTER TABLE warehouses ADD CONSTRAINT warehouses_status_check
        CHECK (status IN ('AVAILABLE', 'RENTED', 'PENDING_APPROVAL', 'INACTIVE'));
    END IF;
END $$;
