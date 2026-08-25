-- A9: paid warehouse publication periods and listing order history.
-- Public visibility filtering and removal of the legacy posting fee belong to A10.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE warehouses
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS visible_until TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_warehouses_visible_until
    ON warehouses (visible_until);

CREATE TABLE IF NOT EXISTS listing_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id),
    warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    listing_package_id UUID NOT NULL REFERENCES listing_packages(id),
    duration_days_snapshot INTEGER NOT NULL,
    price_snapshot NUMERIC(15, 2) NOT NULL,
    period_start TIMESTAMP NOT NULL,
    period_end TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT listing_orders_duration_days_check
        CHECK (duration_days_snapshot IN (10, 15, 30)),
    CONSTRAINT listing_orders_price_check
        CHECK (price_snapshot >= 0),
    CONSTRAINT listing_orders_period_check
        CHECK (period_end > period_start)
);

CREATE INDEX IF NOT EXISTS idx_listing_orders_owner_id
    ON listing_orders (owner_id);

CREATE INDEX IF NOT EXISTS idx_listing_orders_warehouse_id
    ON listing_orders (warehouse_id);

CREATE INDEX IF NOT EXISTS idx_listing_orders_created_at
    ON listing_orders (created_at);

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS listing_order_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_listing_order_id
    ON transactions (listing_order_id)
    WHERE listing_order_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions') THEN
        ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_transaction_type_check;
        ALTER TABLE transactions ADD CONSTRAINT transactions_transaction_type_check
        CHECK (transaction_type IN (
            'TOP_UP', 'WITHDRAWAL', 'DEPOSIT_PAYMENT', 'DEPOSIT_RECEIVED',
            'DEPOSIT_REFUND', 'PACKAGE_PAYMENT', 'COMMISSION', 'LISTING_FEE'
        ));
    END IF;
END $$;
