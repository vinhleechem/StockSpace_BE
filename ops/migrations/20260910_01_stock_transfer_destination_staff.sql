-- Assign a receiving staff member at the current destination while preserving
-- the receiver snapshot on every physical transfer attempt.

ALTER TABLE public.stock_transfers
    ADD COLUMN IF NOT EXISTS assigned_destination_staff_id UUID
        REFERENCES public.users(id);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_assigned_destination_staff
    ON public.stock_transfers (assigned_destination_staff_id);

ALTER TABLE public.stock_transfer_attempts
    ADD COLUMN IF NOT EXISTS assigned_destination_staff_id UUID
        REFERENCES public.users(id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_attempts_assigned_destination_staff
    ON public.stock_transfer_attempts (assigned_destination_staff_id);
