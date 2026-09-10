-- Allow a submitted audit to be opened for an in-place correction only after
-- the assigned staff member requests it and the tenant approves it.
ALTER TABLE public.inventory_audits
    ADD COLUMN IF NOT EXISTS edit_requested_by UUID REFERENCES public.users(id),
    ADD COLUMN IF NOT EXISTS edit_requested_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS edit_approved_by UUID REFERENCES public.users(id),
    ADD COLUMN IF NOT EXISTS edit_approved_at TIMESTAMP;

ALTER TABLE public.inventory_audits
    DROP CONSTRAINT IF EXISTS inventory_audits_status_check;

ALTER TABLE public.inventory_audits
    ADD CONSTRAINT inventory_audits_status_check
    CHECK (status IN (
        'PENDING',
        'DRAFT',
        'IN_PROGRESS',
        'SUBMITTED',
        'EDIT_REQUESTED',
        'REOPENED',
        'RECOUNT_REQUIRED',
        'APPROVED',
        'REJECTED',
        'CANCELLED'
    ));
