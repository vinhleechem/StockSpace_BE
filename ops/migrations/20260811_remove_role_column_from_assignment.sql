-- Migration: Remove role column from staff_warehouse_assignments table
-- Date: 2026-08-11
-- Reason: Staff sub-roles are deprecated, and role column has been removed from Java Entity.

ALTER TABLE staff_warehouse_assignments DROP COLUMN IF EXISTS role;
