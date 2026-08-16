-- Package features are display metadata only.
-- max_staff is stored in service_packages.max_staff and is not duplicated in JSON.
-- max_products is not a supported quota and must not be advertised.

DO $$
DECLARE
    package_row RECORD;
    normalized_features JSONB;
BEGIN
    FOR package_row IN
        SELECT id, features
        FROM service_packages
        WHERE features IS NOT NULL
          AND (features ~ '"max_staff"\s*:' OR features ~ '"max_products"\s*:')
    LOOP
        BEGIN
            normalized_features := package_row.features::JSONB;

            IF jsonb_typeof(normalized_features) = 'object' THEN
                normalized_features := normalized_features - 'max_staff' - 'max_products';

                UPDATE service_packages
                SET features = normalized_features::TEXT
                WHERE id = package_row.id;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            -- Keep non-JSON human-readable descriptions unchanged.
            NULL;
        END;
    END LOOP;
END $$;
