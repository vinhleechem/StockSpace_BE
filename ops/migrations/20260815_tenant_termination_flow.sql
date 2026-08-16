DO $$
BEGIN
    IF EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'rental_contracts') THEN
        ALTER TABLE rental_contracts
            ADD COLUMN IF NOT EXISTS tenant_termination BOOLEAN NOT NULL DEFAULT FALSE,
            ADD COLUMN IF NOT EXISTS deposit_forfeited BOOLEAN NOT NULL DEFAULT FALSE;

        ALTER TABLE rental_contracts DROP CONSTRAINT IF EXISTS rental_contracts_status_check;
        ALTER TABLE rental_contracts ADD CONSTRAINT rental_contracts_status_check
        CHECK (status IN ('UNDER_NEGOTIATION', 'PENDING_TENANT_CONFIRM', 'ACTIVE', 'PENDING_TERMINATION',
                          'PENDING_CANCEL', 'CANCELLED', 'PENDING_HANDOVER', 'COMPLETED', 'DISPUTED'));
    END IF;
END $$;
