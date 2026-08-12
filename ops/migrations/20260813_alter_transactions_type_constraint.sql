-- Migration: Update check constraints for all dynamic Enum fields across tables
-- Date: 2026-08-13
-- Reason: To prevent PostgreSQL check constraint violations when updated Enum values are saved in Production.

-- 1. Transactions table (transaction_type)
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_transaction_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_transaction_type_check 
CHECK (transaction_type IN ('TOP_UP', 'WITHDRAWAL', 'DEPOSIT_PAYMENT', 'DEPOSIT_RECEIVED', 'DEPOSIT_REFUND', 'PACKAGE_PAYMENT', 'COMMISSION'));

-- 2. Rental Contracts table (status)
ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_status_check;
ALTER TABLE rental_contracts ADD CONSTRAINT rental_contracts_status_check 
CHECK (status IN ('UNDER_NEGOTIATION', 'PENDING_TENANT_CONFIRM', 'ACTIVE', 'PENDING_CANCEL', 'CANCELLED', 'PENDING_HANDOVER', 'COMPLETED', 'DISPUTED'));

-- 3. User Subscriptions table (status)
ALTER TABLE user_subscriptions DROP CONSTRAINT IF EXISTS user_subscriptions_status_check;
ALTER TABLE user_subscriptions ADD CONSTRAINT user_subscriptions_status_check 
CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED', 'SUPERSEDED'));

-- 4. Warehouses table (status)
ALTER TABLE warehouses DROP CONSTRAINT IF EXISTS warehouses_status_check;
ALTER TABLE warehouses ADD CONSTRAINT warehouses_status_check 
CHECK (status IN ('AVAILABLE', 'RENTED', 'PENDING_APPROVAL', 'INACTIVE'));
