-- Remove the confirmed test-only layout records without destroying receipt history.
-- The IDs and parent rack IDs are intentionally guarded so this migration cannot
-- silently target a different record if the database has been reseeded.

DO $migration$
DECLARE
    target_count integer;
    deleted_batch_count integer;
    target_bins CONSTANT uuid[] := ARRAY[
        '13d635a9-3f5a-4364-bf91-42ee6c8eff51'::uuid,
        'd5e4b0de-7452-4986-bcbf-f0d23baebaef'::uuid,
        'd1277925-f4d0-4c6c-a3d2-f7cb9d66ae8d'::uuid,
        'a0dab513-a495-4510-9bd1-9b9a2aad0aa4'::uuid
    ];
BEGIN
    WITH expected(id, code, rack_id) AS (
        VALUES
            ('13d635a9-3f5a-4364-bf91-42ee6c8eff51'::uuid, 'BIN-182305', 'de25ca5e-6123-4f28-9d2a-d0c0be6839a1'::uuid),
            ('d5e4b0de-7452-4986-bcbf-f0d23baebaef'::uuid, 'BIN-182305', '8e99e443-4574-4056-92d7-2a70f0dd707f'::uuid),
            ('d1277925-f4d0-4c6c-a3d2-f7cb9d66ae8d'::uuid, 'BIN-595441', 'b2beb0f6-12d1-4fa3-8760-090464895df1'::uuid),
            ('a0dab513-a495-4510-9bd1-9b9a2aad0aa4'::uuid, 'BIN-585196', 'b2beb0f6-12d1-4fa3-8760-090464895df1'::uuid)
    )
    SELECT COUNT(*)
    INTO target_count
    FROM expected e
    JOIN warehouse_bins b ON b.id = e.id
                         AND b.code = e.code
                         AND b.rack_id = e.rack_id;

    IF target_count <> 4 THEN
        RAISE EXCEPTION
            'Test layout cleanup guard failed: expected 4 matching bin records, found %',
            target_count;
    END IF;

    UPDATE stock_batches
    SET is_active = false,
        is_deleted = true,
        updated_at = CURRENT_TIMESTAMP
    WHERE bin_id = ANY (target_bins)
      AND is_deleted = false;

    GET DIAGNOSTICS deleted_batch_count = ROW_COUNT;

    UPDATE warehouse_bins
    SET is_active = false,
        is_deleted = true,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = ANY (target_bins)
      AND is_deleted = false;

    RAISE NOTICE 'Soft-deleted % test stock batches and 4 test bins.', deleted_batch_count;
END
$migration$;
