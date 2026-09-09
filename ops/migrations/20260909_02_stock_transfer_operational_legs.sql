-- Operational recovery workflow for real-world transport exceptions.
-- A transfer document keeps the original route; every physical retry/return
-- is represented by an immutable sequence-numbered attempt.

ALTER TABLE public.stock_transfers
    ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE public.stock_transfers
    ADD COLUMN IF NOT EXISTS active_destination_warehouse_id UUID REFERENCES public.warehouses(id);

ALTER TABLE public.stock_transfers
    ADD COLUMN IF NOT EXISTS expected_arrival_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS overdue_at TIMESTAMP;

UPDATE public.stock_transfers
SET active_destination_warehouse_id = destination_warehouse_id
WHERE active_destination_warehouse_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_stock_transfers_expected_arrival
    ON public.stock_transfers (status, expected_arrival_at)
    WHERE is_active = true AND is_deleted = false;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_stock_transfers_status'
          AND conrelid = 'public.stock_transfers'::regclass
    ) THEN
        ALTER TABLE public.stock_transfers DROP CONSTRAINT ck_stock_transfers_status;
    END IF;
END
$migration$;

ALTER TABLE public.stock_transfers
    ADD CONSTRAINT ck_stock_transfers_status
    CHECK (status IN (
        'DRAFT', 'PENDING', 'ALLOCATED', 'PICKING', 'READY_TO_DISPATCH',
        'IN_TRANSIT', 'OVERDUE', 'ARRIVED_AT_DESTINATION', 'RECEIVING',
        'PARTIALLY_RECEIVED', 'SHORT_RECEIVED', 'RECEIVE_REJECTED',
        'RECONCILING', 'RETRY_REQUESTED', 'RETURN_REQUESTED',
        'RETURN_IN_TRANSIT', 'PARTIALLY_RETURNED', 'RETURNED',
        'COMPLETED', 'LOST', 'REJECTED', 'CANCELLED'
    ));

ALTER TABLE public.stock_transfer_items
    ADD COLUMN IF NOT EXISTS returned_quantity INTEGER NOT NULL DEFAULT 0;

DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_stock_transfer_items_counters_non_negative'
          AND conrelid = 'public.stock_transfer_items'::regclass
    ) THEN
        ALTER TABLE public.stock_transfer_items DROP CONSTRAINT ck_stock_transfer_items_counters_non_negative;
    END IF;
END
$migration$;

ALTER TABLE public.stock_transfer_items
    ADD CONSTRAINT ck_stock_transfer_items_counters_non_negative
    CHECK (
        reserved_quantity >= 0
        AND picked_quantity >= 0
        AND shipped_quantity >= 0
        AND received_quantity >= 0
        AND received_good_quantity >= 0
        AND received_damaged_quantity >= 0
        AND returned_quantity >= 0
        AND reserved_quantity <= requested_quantity
        AND picked_quantity <= requested_quantity
        AND shipped_quantity <= requested_quantity
        AND received_quantity <= requested_quantity
        AND received_good_quantity + received_damaged_quantity <= received_quantity
        AND received_good_quantity + returned_quantity <= shipped_quantity
    );

CREATE TABLE IF NOT EXISTS public.stock_transfer_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES public.stock_transfers(id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED',
    source_warehouse_id UUID NOT NULL REFERENCES public.warehouses(id),
    destination_warehouse_id UUID NOT NULL REFERENCES public.warehouses(id),
    planned_quantity INTEGER NOT NULL,
    shipped_quantity INTEGER NOT NULL DEFAULT 0,
    received_quantity INTEGER NOT NULL DEFAULT 0,
    reason TEXT,
    created_by UUID REFERENCES public.users(id),
    started_at TIMESTAMP,
    arrived_at TIMESTAMP,
    completed_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ux_stock_transfer_attempts_transfer_sequence UNIQUE (transfer_id, sequence_no),
    CONSTRAINT ck_stock_transfer_attempts_type CHECK (type IN ('OUTBOUND', 'FORWARD', 'RETURN')),
    CONSTRAINT ck_stock_transfer_attempts_status CHECK (
        status IN ('PLANNED', 'IN_TRANSIT', 'ARRIVED', 'RECEIVING', 'RECEIVED',
                   'REJECTED', 'PARTIALLY_RECEIVED', 'RETURNED', 'CANCELLED')
    ),
    CONSTRAINT ck_stock_transfer_attempts_quantities CHECK (
        planned_quantity > 0
        AND shipped_quantity >= 0
        AND received_quantity >= 0
        AND shipped_quantity <= planned_quantity
        AND received_quantity <= shipped_quantity
    )
);

ALTER TABLE public.stock_transfer_events
    ADD COLUMN IF NOT EXISTS attempt_id UUID REFERENCES public.stock_transfer_attempts(id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_events_attempt
    ON public.stock_transfer_events (attempt_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_attempts_transfer_sequence
    ON public.stock_transfer_attempts (transfer_id, sequence_no);

-- Backfill one operational outbound leg for every pre-existing transfer so
-- historical documents have a complete timeline immediately after upgrade.
INSERT INTO public.stock_transfer_attempts (
    transfer_id, sequence_no, type, status, source_warehouse_id,
    destination_warehouse_id, planned_quantity, shipped_quantity,
    received_quantity, created_by, started_at, arrived_at, completed_at,
    created_at, updated_at
)
SELECT t.id,
       1,
       'OUTBOUND',
       CASE
           WHEN t.status IN ('COMPLETED', 'RECONCILING') THEN 'RECEIVED'
           WHEN t.status IN ('IN_TRANSIT', 'OVERDUE', 'PARTIALLY_RECEIVED') THEN 'IN_TRANSIT'
           ELSE 'PLANNED'
       END,
       t.source_warehouse_id,
       COALESCE(t.active_destination_warehouse_id, t.destination_warehouse_id),
       GREATEST(1, COALESCE((SELECT SUM(i.requested_quantity)
                             FROM public.stock_transfer_items i
                             WHERE i.transfer_id = t.id), 1)),
       COALESCE((SELECT SUM(i.shipped_quantity)
                 FROM public.stock_transfer_items i
                 WHERE i.transfer_id = t.id), 0),
       COALESCE((SELECT SUM(i.received_quantity)
                 FROM public.stock_transfer_items i
                 WHERE i.transfer_id = t.id), 0),
       t.created_by,
       t.approved_at,
       CASE WHEN t.status IN ('COMPLETED', 'RECONCILING') THEN t.received_at END,
       CASE WHEN t.status IN ('COMPLETED') THEN t.received_at END,
       t.created_at,
       t.updated_at
FROM public.stock_transfers t
WHERE NOT EXISTS (
    SELECT 1 FROM public.stock_transfer_attempts a WHERE a.transfer_id = t.id
);

UPDATE public.stock_transfer_events e
SET attempt_id = a.id
FROM public.stock_transfer_attempts a
WHERE e.transfer_id = a.transfer_id
  AND a.sequence_no = 1
  AND e.attempt_id IS NULL;
