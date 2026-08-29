-- A3: persist the optional note attached by the Owner to a direct Draft Contract.
-- No existing rental-contract field has this meaning, so this is a new nullable
-- field and does not alter Booking or historical contract data.

ALTER TABLE rental_contracts
    ADD COLUMN IF NOT EXISTS owner_note TEXT;
