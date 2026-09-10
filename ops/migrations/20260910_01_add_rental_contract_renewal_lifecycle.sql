-- Add the successor relationship and scheduled lifecycle for contract renewal.
-- This migration is additive: existing contracts are not re-linked or rewritten.

DO $migration$
BEGIN
    IF to_regclass('public.rental_contracts') IS NULL THEN
        RAISE EXCEPTION
            'Contract renewal migration requires public.rental_contracts';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'rental_contracts'
          AND column_name = 'renewed_from_contract_id'
    ) THEN
        ALTER TABLE public.rental_contracts
            ADD COLUMN renewed_from_contract_id UUID;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.rental_contracts'::regclass
          AND conname = 'fk_rental_contracts_renewed_from'
    ) THEN
        ALTER TABLE public.rental_contracts
            ADD CONSTRAINT fk_rental_contracts_renewed_from
            FOREIGN KEY (renewed_from_contract_id)
            REFERENCES public.rental_contracts (id)
            ON DELETE RESTRICT;
    END IF;
END
$migration$;

ALTER TABLE public.rental_contracts
    DROP CONSTRAINT IF EXISTS ck_rental_contracts_not_self_renewal;

ALTER TABLE public.rental_contracts
    ADD CONSTRAINT ck_rental_contracts_not_self_renewal
    CHECK (
        renewed_from_contract_id IS NULL
        OR renewed_from_contract_id <> id
    );

ALTER TABLE public.rental_contracts
    DROP CONSTRAINT IF EXISTS rental_contracts_status_check;

ALTER TABLE public.rental_contracts
    ADD CONSTRAINT rental_contracts_status_check
    CHECK (status IN (
        'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
        'SCHEDULED', 'ACTIVE', 'REJECTED', 'EXPIRED'
    ));

CREATE INDEX IF NOT EXISTS idx_rental_contracts_renewed_from
    ON public.rental_contracts (renewed_from_contract_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rental_contracts_blocking_renewal_source
    ON public.rental_contracts (renewed_from_contract_id)
    WHERE renewed_from_contract_id IS NOT NULL
      AND status IN (
          'DRAFT', 'PENDING_TENANT_CONFIRM', 'CHANGES_REQUESTED',
          'SCHEDULED', 'ACTIVE', 'EXPIRED'
      )
      AND is_active = TRUE
      AND is_deleted = FALSE;

DROP INDEX IF EXISTS public.idx_rental_contracts_actionable_period;

CREATE INDEX IF NOT EXISTS idx_rental_contracts_actionable_period
    ON public.rental_contracts (tenant_id, warehouse_id, start_date, end_date)
    WHERE status IN ('PENDING_TENANT_CONFIRM', 'SCHEDULED', 'ACTIVE')
      AND is_active = TRUE
      AND is_deleted = FALSE;
