-- Backfill the normalized staff limit used by the API and staff enforcement.
-- The original seed stored max_staff only inside the features JSON, while the
-- application reads service_packages.max_staff.

ALTER TABLE service_packages
    ADD COLUMN IF NOT EXISTS max_staff INTEGER;

DO $$
DECLARE
    package_row RECORD;
    extracted_max_staff INTEGER;
BEGIN
    FOR package_row IN
        SELECT id, features, max_staff
        FROM service_packages
    LOOP
        IF COALESCE(package_row.max_staff, 0) = 0
           AND package_row.features IS NOT NULL
           AND package_row.features ~ '"max_staff"\s*:\s*[0-9]+'
        THEN
            extracted_max_staff :=
                ((regexp_match(package_row.features, '"max_staff"\s*:\s*([0-9]+)'))[1])::INTEGER;

            UPDATE service_packages
            SET max_staff = extracted_max_staff
            WHERE id = package_row.id;
        END IF;
    END LOOP;
END $$;

-- Repair snapshots created before the normalized package column was populated.
-- This keeps the active subscription response and staff enforcement consistent
-- with the package that was actually purchased.
UPDATE subscriptions s
SET snapshot_max_staff = p.max_staff
FROM service_packages p
WHERE s.package_id = p.id
  AND COALESCE(s.snapshot_max_staff, 0) = 0
  AND COALESCE(p.max_staff, 0) > 0;
