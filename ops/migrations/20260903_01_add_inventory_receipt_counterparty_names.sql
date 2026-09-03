-- Store the external sender for inbound receipts and the receiver for outbound receipts.
-- Both columns stay nullable so existing receipts and system-generated adjustments remain valid.

DO $migration$
BEGIN
    IF to_regclass('public.inventory_receipts') IS NULL THEN
        RAISE EXCEPTION
            'Inventory receipt counterparty migration requires inventory_receipts';
    END IF;
END
$migration$;

ALTER TABLE public.inventory_receipts
    ADD COLUMN IF NOT EXISTS sender_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS receiver_name VARCHAR(255);
