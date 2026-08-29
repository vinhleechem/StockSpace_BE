-- Contract expiry reminder and cleanup support.
-- Expiry processing is intentionally soft-delete only for WMS/layout rows so
-- receipt and inventory transaction history remains auditable.

ALTER TABLE rental_contracts
    ADD COLUMN IF NOT EXISTS expiry_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;
