-- Migration: Add positions column to warehouse_layouts
-- Date: 2026-08-13
-- Reason: Store the grid cell lock/paint states array sent by FE (e.g. ["1:0","2:1",...])

DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'warehouse_layouts') THEN
        ALTER TABLE warehouse_layouts ADD COLUMN IF NOT EXISTS positions TEXT;
    END IF;
END $$;
