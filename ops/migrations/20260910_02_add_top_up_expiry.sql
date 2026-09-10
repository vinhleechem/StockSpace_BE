-- Track the VNPAY payment deadline so abandoned top-up attempts do not stay
-- PENDING forever. Existing pending rows are backfilled from their creation
-- time using the gateway's configured 15-minute payment window.

DO $migration$
BEGIN
    IF to_regclass('public.transactions') IS NULL THEN
        RAISE EXCEPTION
            'Top-up expiry migration requires public.transactions';
    END IF;
END
$migration$;

ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

UPDATE public.transactions
SET expires_at = created_at + INTERVAL '15 minutes'
WHERE transaction_type = 'TOP_UP'
  AND status = 'PENDING'
  AND expires_at IS NULL
  AND created_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_transactions_top_up_expiry
    ON public.transactions (expires_at)
    WHERE transaction_type = 'TOP_UP'
      AND status = 'PENDING';
