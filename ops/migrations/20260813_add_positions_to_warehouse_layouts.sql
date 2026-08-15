
DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'warehouse_layouts') THEN
        ALTER TABLE warehouse_layouts ADD COLUMN IF NOT EXISTS positions TEXT;
    END IF;
END $$;
