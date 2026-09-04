-- Adds immutable tenant ownership to inventory receipts.
-- Run before deploying the application version that maps tenant_id as NOT NULL.
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.inventory_receipts') IS NULL
       OR to_regclass('public.inventory_receipt_items') IS NULL
       OR to_regclass('public.product_skus') IS NULL
       OR to_regclass('public.tenant_members') IS NULL THEN
        RAISE EXCEPTION
            'Inventory receipt tenant migration requires inventory_receipts, inventory_receipt_items, product_skus and tenant_members';
    END IF;
END $$;

ALTER TABLE public.inventory_receipts
    ADD COLUMN IF NOT EXISTS tenant_id uuid;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.inventory_receipt_items item
        JOIN public.product_skus sku ON sku.id = item.sku_id
        WHERE sku.tenant_id IS NOT NULL
        GROUP BY item.receipt_id
        HAVING COUNT(DISTINCT sku.tenant_id) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot infer tenant_id: at least one inventory receipt contains SKUs from multiple tenants';
    END IF;
END $$;

UPDATE public.inventory_receipts receipt
SET tenant_id = COALESCE(
    (
        SELECT sku.tenant_id
        FROM public.inventory_receipt_items item
        JOIN public.product_skus sku ON sku.id = item.sku_id
        WHERE item.receipt_id = receipt.id
          AND sku.tenant_id IS NOT NULL
        LIMIT 1
    ),
    (
        SELECT member.tenant_id
        FROM public.tenant_members member
        WHERE member.user_id = receipt.created_by
        ORDER BY
            CASE
                WHEN member.joined_at <= COALESCE(receipt.created_at, CURRENT_TIMESTAMP) THEN 0
                ELSE 1
            END,
            member.joined_at DESC
        LIMIT 1
    ),
    receipt.created_by
)
WHERE receipt.tenant_id IS NULL;

ALTER TABLE public.inventory_receipts
    ALTER COLUMN tenant_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_inventory_receipts_tenant'
          AND conrelid = 'public.inventory_receipts'::regclass
    ) THEN
        ALTER TABLE public.inventory_receipts
            ADD CONSTRAINT fk_inventory_receipts_tenant
            FOREIGN KEY (tenant_id) REFERENCES public.users(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_inventory_receipts_tenant_warehouse
    ON public.inventory_receipts (tenant_id, warehouse_id);
CREATE INDEX IF NOT EXISTS idx_inventory_receipts_tenant_status
    ON public.inventory_receipts (tenant_id, status);

COMMIT;
