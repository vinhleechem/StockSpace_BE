-- A8: catalog of warehouse listing publication packages.
-- This is intentionally separate from service_packages. The legacy posting
-- fee package/config remains untouched until the publication flow is migrated.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS listing_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    duration_days INTEGER NOT NULL,
    price NUMERIC(15, 2) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_listing_packages_duration_days
    ON listing_packages (duration_days);

-- Initial team pricing. Admin can change these values through the admin API;
-- FE must load the current values from GET /api/listing-packages.
INSERT INTO listing_packages (
    id, name, duration_days, price, is_active, is_deleted, created_at, updated_at
)
VALUES
    (gen_random_uuid(), 'Listing Package - 10 Days', 10, 50000.00, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'Listing Package - 15 Days', 15, 70000.00, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'Listing Package - 30 Days', 30, 120000.00, TRUE, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (duration_days) DO NOTHING;
