-- The canonical audit workflow introduces DRAFT, IN_PROGRESS,
-- RECOUNT_REQUIRED, and CANCELLED. Replace the legacy status check while
-- retaining historical values so existing rows remain readable.
ALTER TABLE public.inventory_audits
    DROP CONSTRAINT IF EXISTS inventory_audits_status_check;

ALTER TABLE public.inventory_audits
    ADD CONSTRAINT inventory_audits_status_check
    CHECK (status IN (
        'PENDING',
        'DRAFT',
        'IN_PROGRESS',
        'SUBMITTED',
        'RECOUNT_REQUIRED',
        'APPROVED',
        'REJECTED',
        'CANCELLED'
    ));
