CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS stock_transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES users(id),
    source_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses(id),
    status VARCHAR(20) NOT NULL,
    note TEXT,
    created_by UUID NOT NULL REFERENCES users(id),
    approved_by UUID REFERENCES users(id),
    received_by UUID REFERENCES users(id),
    rejected_by UUID REFERENCES users(id),
    cancelled_by UUID REFERENCES users(id),
    decision_reason TEXT,
    approved_at TIMESTAMP,
    received_at TIMESTAMP,
    rejected_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    outbound_receipt_id UUID UNIQUE REFERENCES inventory_receipts(id),
    inbound_receipt_id UUID UNIQUE REFERENCES inventory_receipts(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_stock_transfers_different_warehouses
        CHECK (source_warehouse_id <> destination_warehouse_id),
    CONSTRAINT ck_stock_transfers_status
        CHECK (status IN ('PENDING', 'IN_TRANSIT', 'COMPLETED', 'REJECTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS stock_transfer_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
    sku_id UUID NOT NULL REFERENCES product_skus(id),
    requested_quantity INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_stock_transfer_items_quantity
        CHECK (requested_quantity > 0),
    CONSTRAINT ux_stock_transfer_items_transfer_sku
        UNIQUE (transfer_id, sku_id)
);

CREATE TABLE IF NOT EXISTS stock_transfer_source_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES stock_transfer_items(id) ON DELETE CASCADE,
    source_stock_batch_id UUID NOT NULL REFERENCES stock_batches(id),
    source_rack_id UUID NOT NULL REFERENCES warehouse_racks(id),
    source_bin_id UUID NOT NULL REFERENCES warehouse_bins(id),
    quantity INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_stock_transfer_source_alloc_quantity
        CHECK (quantity > 0),
    CONSTRAINT ux_stock_transfer_source_alloc_item_batch
        UNIQUE (item_id, source_stock_batch_id)
);

CREATE TABLE IF NOT EXISTS stock_transfer_destination_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id UUID NOT NULL REFERENCES stock_transfer_items(id) ON DELETE CASCADE,
    destination_rack_id UUID NOT NULL REFERENCES warehouse_racks(id),
    destination_bin_id UUID NOT NULL REFERENCES warehouse_bins(id),
    quantity INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT ck_stock_transfer_destination_alloc_quantity
        CHECK (quantity > 0),
    CONSTRAINT ux_stock_transfer_dest_alloc_item_location
        UNIQUE (item_id, destination_rack_id, destination_bin_id)
);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_tenant_status
    ON stock_transfers (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_source_warehouse
    ON stock_transfers (source_warehouse_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfers_destination_warehouse
    ON stock_transfers (destination_warehouse_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_items_transfer_id
    ON stock_transfer_items (transfer_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_source_alloc_item_id
    ON stock_transfer_source_allocations (item_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_source_alloc_batch_id
    ON stock_transfer_source_allocations (source_stock_batch_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_destination_alloc_item_id
    ON stock_transfer_destination_allocations (item_id);

CREATE INDEX IF NOT EXISTS idx_stock_transfer_destination_alloc_bin_id
    ON stock_transfer_destination_allocations (destination_bin_id);
