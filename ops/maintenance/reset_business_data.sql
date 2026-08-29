-- Destructive maintenance operation: remove all application data while keeping
-- the schema migration ledger. The caller must create and verify a backup first.
DO $reset$
DECLARE
    target_table record;
BEGIN
    FOR target_table IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename <> 'schema_migrations'
        ORDER BY tablename
    LOOP
        EXECUTE format(
            'TRUNCATE TABLE %I.%I RESTART IDENTITY CASCADE',
            target_table.schemaname,
            target_table.tablename
        );
    END LOOP;
END
$reset$;
