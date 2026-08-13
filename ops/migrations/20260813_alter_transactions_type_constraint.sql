-- Migration: Update check constraints for all dynamic Enum fields across tables
-- Date: 2026-08-13
-- Reason: To prevent PostgreSQL check constraint violations when updated Enum values are saved in Production.

-- 1. Transactions table (transaction_type)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions') THEN
        ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_transaction_type_check;
        ALTER TABLE transactions ADD CONSTRAINT transactions_transaction_type_check 
        CHECK (transaction_type IN ('TOP_UP', 'WITHDRAWAL', 'DEPOSIT_PAYMENT', 'DEPOSIT_RECEIVED', 'DEPOSIT_REFUND', 'PACKAGE_PAYMENT', 'COMMISSION'));
    END IF;
END $$;

-- 2. Rental Contracts table (status)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'rental_contracts') THEN
        ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_status_check;
        ALTER TABLE rental_contracts ADD CONSTRAINT rental_contracts_status_check 
        CHECK (status IN ('UNDER_NEGOTIATION', 'PENDING_TENANT_CONFIRM', 'ACTIVE', 'PENDING_CANCEL', 'CANCELLED', 'PENDING_HANDOVER', 'COMPLETED', 'DISPUTED'));
    END IF;
END $$;

-- 3. Subscriptions table (status)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'subscriptions') THEN
        ALTER TABLE subscriptions DROP CONSTRAINT IF EXISTS subscriptions_status_check;
        ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_status_check 
        CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED', 'SUPERSEDED'));
    END IF;
END $$;

-- 4. Warehouses table (status)
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'warehouses') THEN
        ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS warehouses_status_check;
        ALTER TABLE warehouses ADD CONSTRAINT warehouses_status_check 
        CHECK (status IN ('AVAILABLE', 'RENTED', 'PENDING_APPROVAL', 'INACTIVE'));
    END IF;
END $$;
