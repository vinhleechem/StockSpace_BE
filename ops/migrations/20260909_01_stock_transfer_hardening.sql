-- Production hardening for stock transfer execution.
-- Additive by design: the legacy approve-dispatch endpoint remains usable while
-- newer clients can reserve first and support partial receiving safely.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE public.stock_transfers
    ADD COLUMN IF NOT EXISTS transfer_no VARCHAR(40),
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS assigned_source_staff_id UUID REFERENCES public.users(id),
    ADD COLUMN IF NOT EXISTS active_destination_warehouse_id UUID REFERENCES public.warehouses(id),
    ADD COLUMN IF NOT EXISTS expected_arrival_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS overdue_at TIMESTAMP;

ALTER TABLE public.stock_transfers
    ALTER COLUMN status TYPE VARCHAR(30);

UPDATE public.stock_transfers
SET active_destination_warehouse_id = destination_warehouse_id
WHERE active_destination_warehouse_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_stock_transfers_assigned_source_staff
    ON public.stock_transfers (assigned_source_staff_id);

UPDATE public.stock_transfers
SET transfer_no = 'TRF-' || upper(substr(replace(id::text, '-', ''), 1, 20))
WHERE transfer_no IS NULL;

ALTER TABLE public.stock_transfers
    ALTER COLUMN transfer_no SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_transfers_tenant_transfer_no
    ON public.stock_transfers (tenant_id, transfer_no);

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
    ADD COLUMN IF NOT EXISTS reserved_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS picked_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS shipped_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS received_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS received_good_quantity INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS received_damaged_quantity INTEGER NOT NULL DEFAULT 0,
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

ALTER TABLE public.stock_transfer_destination_allocations
    ADD COLUMN IF NOT EXISTS disposition VARCHAR(20) NOT NULL DEFAULT 'GOOD';

-- A physical location may contain both accepted and quarantined quantities
-- across separate receiving sessions.  Keep one row per disposition.
DO $migration$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ux_stock_transfer_dest_alloc_item_location'
          AND conrelid = 'public.stock_transfer_destination_allocations'::regclass
    ) THEN
        ALTER TABLE public.stock_transfer_destination_allocations
            DROP CONSTRAINT ux_stock_transfer_dest_alloc_item_location;
    END IF;
END
$migration$;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ux_stock_transfer_dest_alloc_item_location_disposition'
          AND conrelid = 'public.stock_transfer_destination_allocations'::regclass
    ) THEN
        ALTER TABLE public.stock_transfer_destination_allocations
            ADD CONSTRAINT ux_stock_transfer_dest_alloc_item_location_disposition
            UNIQUE (item_id, destination_rack_id, destination_bin_id, disposition);
    END IF;
END
$migration$;

DO $migration$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_stock_transfer_destination_disposition'
          AND conrelid = 'public.stock_transfer_destination_allocations'::regclass
    ) THEN
        ALTER TABLE public.stock_transfer_destination_allocations
            ADD CONSTRAINT ck_stock_transfer_destination_disposition
            CHECK (disposition IN ('GOOD', 'QUARANTINE', 'DAMAGED', 'REJECTED'));
    END IF;
END
$migration$;

-- Preserve the meaning of already completed MVP transfers in the new counters.
UPDATE public.stock_transfer_items i
SET received_quantity = COALESCE(t.total_received, 0),
    received_good_quantity = COALESCE(t.good_received, 0),
    received_damaged_quantity = COALESCE(t.damaged_received, 0),
    shipped_quantity = CASE
        WHEN i.transfer_id IN (
            SELECT id FROM public.stock_transfers
            WHERE status IN ('IN_TRANSIT', 'OVERDUE', 'PARTIALLY_RECEIVED', 'RECONCILING', 'COMPLETED')
        ) THEN i.requested_quantity ELSE i.shipped_quantity END,
    picked_quantity = CASE
        WHEN i.transfer_id IN (
            SELECT id FROM public.stock_transfers
            WHERE status IN ('IN_TRANSIT', 'OVERDUE', 'PARTIALLY_RECEIVED', 'RECONCILING', 'COMPLETED')
        ) THEN i.requested_quantity ELSE i.picked_quantity END
FROM (
    SELECT item_id,
           SUM(quantity) AS total_received,
           SUM(CASE WHEN disposition = 'GOOD' THEN quantity ELSE 0 END) AS good_received,
           SUM(CASE WHEN disposition IN ('DAMAGED', 'QUARANTINE', 'REJECTED') THEN quantity ELSE 0 END)
               AS damaged_received
    FROM public.stock_transfer_destination_allocations
    GROUP BY item_id
) t
WHERE i.id = t.item_id;

UPDATE public.stock_transfer_items i
SET shipped_quantity = i.requested_quantity,
    picked_quantity = i.requested_quantity
FROM public.stock_transfers t
WHERE i.transfer_id = t.id
  AND t.status IN ('IN_TRANSIT', 'OVERDUE', 'PARTIALLY_RECEIVED', 'RECONCILING', 'COMPLETED');

CREATE TABLE IF NOT EXISTS public.stock_transfer_reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_item_id UUID NOT NULL REFERENCES public.stock_transfer_items(id) ON DELETE CASCADE,
    source_stock_batch_id UUID NOT NULL REFERENCES public.stock_batches(id),
    quantity INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    reserved_by UUID NOT NULL REFERENCES public.users(id),
    reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    consumed_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_stock_transfer_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_transfer_reservation_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_reservation_batch_status
    ON public.stock_transfer_reservations (source_stock_batch_id, status);
CREATE INDEX IF NOT EXISTS idx_stock_transfer_reservation_item
    ON public.stock_transfer_reservations (transfer_item_id);

CREATE TABLE IF NOT EXISTS public.stock_transfer_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES public.stock_transfers(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    command VARCHAR(50) NOT NULL,
    actor_id UUID NOT NULL REFERENCES public.users(id),
    reason TEXT,
    idempotency_key VARCHAR(120),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_events_transfer_created
    ON public.stock_transfer_events (transfer_id, created_at);

CREATE TABLE IF NOT EXISTS public.stock_transfer_commands (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.users(id),
    transfer_id UUID NOT NULL REFERENCES public.stock_transfers(id) ON DELETE CASCADE,
    command VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    result_status VARCHAR(30) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ux_stock_transfer_commands_key
        UNIQUE (tenant_id, command, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_commands_transfer
    ON public.stock_transfer_commands (transfer_id);

CREATE TABLE IF NOT EXISTS public.stock_transfer_pick_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES public.stock_transfers(id) ON DELETE CASCADE,
    item_id UUID NOT NULL REFERENCES public.stock_transfer_items(id),
    source_allocation_id UUID NOT NULL REFERENCES public.stock_transfer_source_allocations(id),
    source_stock_batch_id UUID NOT NULL REFERENCES public.stock_batches(id),
    source_rack_id UUID NOT NULL REFERENCES public.warehouse_racks(id),
    source_bin_id UUID NOT NULL REFERENCES public.warehouse_bins(id),
    quantity INTEGER NOT NULL,
    picked_by UUID NOT NULL REFERENCES public.users(id),
    picked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_stock_transfer_pick_quantity CHECK (quantity > 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_pick_lines_transfer
    ON public.stock_transfer_pick_lines (transfer_id);
CREATE INDEX IF NOT EXISTS idx_stock_transfer_pick_lines_allocation
    ON public.stock_transfer_pick_lines (source_allocation_id);

-- A batch can be reserved by many transfers, but one transfer/item/batch pair
-- must never have two active reservation rows.
CREATE UNIQUE INDEX IF NOT EXISTS ux_stock_transfer_active_reservation
    ON public.stock_transfer_reservations (transfer_item_id, source_stock_batch_id)
    WHERE status = 'ACTIVE' AND is_deleted = false;

DO $migration$
BEGIN
    IF to_regclass('public.stock_batches') IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM pg_constraint
           WHERE conname = 'ck_stock_batches_quantity_non_negative'
             AND conrelid = 'public.stock_batches'::regclass
       ) THEN
        ALTER TABLE public.stock_batches
            ADD CONSTRAINT ck_stock_batches_quantity_non_negative CHECK (quantity >= 0);
    END IF;
END
$migration$;
