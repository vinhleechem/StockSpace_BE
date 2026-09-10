-- Keep the database status constraint aligned with TransactionStatus.
-- Top-up expiry intentionally persists EXPIRED instead of reverting to FAILED.

DO $migration$
BEGIN
    IF to_regclass('public.transactions') IS NULL THEN
        RAISE EXCEPTION
            'Transaction status migration requires public.transactions';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.transactions
        WHERE status IS NULL
           OR status NOT IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED')
    ) THEN
        RAISE EXCEPTION
            'Transaction status migration stopped: unsupported status value exists';
    END IF;

    ALTER TABLE public.transactions
        DROP CONSTRAINT IF EXISTS transactions_status_check;

    ALTER TABLE public.transactions
        ADD CONSTRAINT transactions_status_check
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED'));
END
$migration$;
