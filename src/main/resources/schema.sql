CREATE EXTENSION IF NOT EXISTS vector;

-- inventory_receipts originally had no immutable tenant owner. Backfill it
-- before Hibernate validates the new non-null relationship. SKU ownership is
-- preferred; staff membership at receipt creation time is the fallback.
DO $$
BEGIN
    IF to_regclass('public.inventory_receipts') IS NOT NULL THEN
        ALTER TABLE inventory_receipts ADD COLUMN IF NOT EXISTS tenant_id uuid;

        IF EXISTS (
            SELECT 1
            FROM inventory_receipt_items item
            JOIN product_skus sku ON sku.id = item.sku_id
            WHERE sku.tenant_id IS NOT NULL
            GROUP BY item.receipt_id
            HAVING COUNT(DISTINCT sku.tenant_id) > 1
        ) THEN
            RAISE EXCEPTION
                'Cannot infer tenant_id: an inventory receipt contains SKUs from multiple tenants';
        END IF;

        UPDATE inventory_receipts receipt
        SET tenant_id = COALESCE(
            (
                SELECT sku.tenant_id
                FROM inventory_receipt_items item
                JOIN product_skus sku ON sku.id = item.sku_id
                WHERE item.receipt_id = receipt.id
                  AND sku.tenant_id IS NOT NULL
                LIMIT 1
            ),
            (
                SELECT member.tenant_id
                FROM tenant_members member
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

        ALTER TABLE inventory_receipts ALTER COLUMN tenant_id SET NOT NULL;
    END IF;
END $$;
