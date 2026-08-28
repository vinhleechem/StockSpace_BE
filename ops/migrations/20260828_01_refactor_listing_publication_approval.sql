-- LP01: add the listing publication approval lifecycle.
-- Existing listing orders were activated at payment time, so historical rows
-- are backfilled as ACTIVATED and keep their original publication period.

ALTER TABLE listing_orders
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVATED';

ALTER TABLE listing_orders
    ALTER COLUMN period_start DROP NOT NULL,
    ALTER COLUMN period_end DROP NOT NULL;

UPDATE listing_orders
SET status = 'ACTIVATED'
WHERE status IS NULL;

ALTER TABLE listing_orders
    DROP CONSTRAINT IF EXISTS listing_orders_status_check,
    DROP CONSTRAINT IF EXISTS listing_orders_period_check;

ALTER TABLE listing_orders
    ADD CONSTRAINT listing_orders_status_check
        CHECK (status IN ('PENDING_APPROVAL', 'ACTIVATED', 'REFUNDED')),
    ADD CONSTRAINT listing_orders_period_check
        CHECK (
            (status = 'ACTIVATED'
                AND period_start IS NOT NULL
                AND period_end IS NOT NULL
                AND period_end > period_start)
            OR
            (status IN ('PENDING_APPROVAL', 'REFUNDED')
                AND period_start IS NULL
                AND period_end IS NULL)
        );

CREATE UNIQUE INDEX IF NOT EXISTS ux_listing_orders_pending_warehouse_id
    ON listing_orders (warehouse_id)
    WHERE status = 'PENDING_APPROVAL' AND is_deleted = FALSE;

DROP INDEX IF EXISTS ux_transactions_listing_order_id;

CREATE UNIQUE INDEX IF NOT EXISTS ux_transactions_listing_order_type
    ON transactions (listing_order_id, transaction_type)
    WHERE listing_order_id IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'transactions') THEN
        ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_transaction_type_check;
        ALTER TABLE transactions ADD CONSTRAINT transactions_transaction_type_check
        CHECK (transaction_type IN (
            'TOP_UP', 'WITHDRAWAL', 'DEPOSIT_PAYMENT', 'DEPOSIT_RECEIVED',
            'DEPOSIT_REFUND', 'PACKAGE_PAYMENT', 'COMMISSION', 'LISTING_FEE',
            'LISTING_REFUND'
        ));
    END IF;
END $$;
