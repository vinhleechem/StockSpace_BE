-- Inventory audit workflow v2. The existing PENDING/SUBMITTED flow remains
-- readable and writable for legacy records; v2 records use workflow_version=2.

ALTER TABLE public.inventory_audits
    ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES public.users(id),
    ADD COLUMN IF NOT EXISTS assigned_to UUID REFERENCES public.users(id),
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(20) NOT NULL DEFAULT 'WAREHOUSE',
    ADD COLUMN IF NOT EXISTS scope_rack_id UUID REFERENCES public.warehouse_racks(id),
    ADD COLUMN IF NOT EXISTS scope_bin_id UUID REFERENCES public.warehouse_bins(id),
    ADD COLUMN IF NOT EXISTS workflow_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS count_round INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS review_reason TEXT,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE public.inventory_audit_items
    ALTER COLUMN batch_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS sku_id UUID,
    ADD COLUMN IF NOT EXISTS rack_id UUID REFERENCES public.warehouse_racks(id),
    ADD COLUMN IF NOT EXISTS bin_id UUID REFERENCES public.warehouse_bins(id),
    ADD COLUMN IF NOT EXISTS count_status VARCHAR(20) NOT NULL DEFAULT 'UNCOUNTED',
    ADD COLUMN IF NOT EXISTS count_round INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS counted_by UUID REFERENCES public.users(id),
    ADD COLUMN IF NOT EXISTS counted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS variance_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_inventory_audits_tenant_status
    ON public.inventory_audits (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_audit_items_round
    ON public.inventory_audit_items (audit_id, count_round, id);

CREATE TABLE IF NOT EXISTS public.inventory_audit_locks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id UUID NOT NULL REFERENCES public.inventory_audits(id),
    warehouse_id UUID NOT NULL REFERENCES public.warehouses(id),
    released_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_inventory_audit_locks_active_warehouse
    ON public.inventory_audit_locks (warehouse_id)
    WHERE released_at IS NULL AND is_active = TRUE AND is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_inventory_audit_locks_audit
    ON public.inventory_audit_locks (audit_id);

CREATE TABLE IF NOT EXISTS public.inventory_audit_adjustments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    audit_id UUID NOT NULL REFERENCES public.inventory_audits(id),
    audit_item_id UUID NOT NULL REFERENCES public.inventory_audit_items(id),
    receipt_id UUID NOT NULL REFERENCES public.inventory_receipts(id),
    batch_id UUID NOT NULL REFERENCES public.stock_batches(id),
    delta INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ux_inventory_audit_adjustment_item_batch UNIQUE (audit_item_id, batch_id)
);

-- Make the migration safe when a development database already contains the first
-- draft of this table (which allowed only one adjustment per audit item).
ALTER TABLE public.inventory_audit_adjustments
    ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES public.stock_batches(id);
ALTER TABLE public.inventory_audit_adjustments
    ALTER COLUMN batch_id SET NOT NULL;
ALTER TABLE public.inventory_audit_adjustments
    DROP CONSTRAINT IF EXISTS ux_inventory_audit_adjustment_item;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ux_inventory_audit_adjustment_item_batch'
          AND conrelid = 'public.inventory_audit_adjustments'::regclass
    ) THEN
        ALTER TABLE public.inventory_audit_adjustments
            ADD CONSTRAINT ux_inventory_audit_adjustment_item_batch UNIQUE (audit_item_id, batch_id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inventory_audit_adjustments_audit
    ON public.inventory_audit_adjustments (audit_id);
