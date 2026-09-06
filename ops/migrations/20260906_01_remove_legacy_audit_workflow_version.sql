-- The audit module now has one canonical workflow. The old workflow marker is
-- no longer read or written by the application, so remove the obsolete column.
ALTER TABLE public.inventory_audits
    DROP COLUMN IF EXISTS workflow_version;
