-- Read-only preflight for the FIFO picking schema migration.
-- Run this file before applying 20260831_01_refactor_stock_batches_for_fifo_picking.sql.
-- Every result set identifies data that must be reviewed before deployment.

-- 1. Positive stock without a reliable arrival timestamp.
SELECT id, sku_id, warehouse_id, rack_id, bin_id, quantity, arrival_date, created_at
FROM public.stock_batches
WHERE is_deleted = false
  AND quantity > 0
  AND arrival_date IS NULL;

-- 2. Positive stock that cannot be tied to a valid physical location.
SELECT b.id AS batch_id,
       b.sku_id,
       b.warehouse_id,
       b.rack_id,
       b.bin_id,
       b.quantity,
       r.layout_id,
       bin.rack_id AS bin_parent_rack_id,
       l.warehouse_id AS layout_warehouse_id
FROM public.stock_batches b
LEFT JOIN public.warehouse_racks r ON r.id = b.rack_id
LEFT JOIN public.warehouse_bins bin ON bin.id = b.bin_id
LEFT JOIN public.warehouse_layouts l ON l.id = r.layout_id
WHERE b.is_deleted = false
  AND b.quantity > 0
  AND (
      b.warehouse_id IS NULL
      OR b.rack_id IS NULL
      OR b.bin_id IS NULL
      OR r.id IS NULL
      OR bin.id IS NULL
      OR l.id IS NULL
      OR r.is_deleted = true
      OR bin.is_deleted = true
      OR l.is_deleted = true
      OR l.warehouse_id IS DISTINCT FROM b.warehouse_id
      OR bin.rack_id IS DISTINCT FROM r.id
  );

-- 3. Active duplicate batches. The old unique index should normally make this empty.
SELECT sku_id, warehouse_id, rack_id, bin_id,
       COUNT(*) AS batch_count,
       ARRAY_AGG(id ORDER BY created_at NULLS LAST, id) AS batch_ids
FROM public.stock_batches
WHERE is_deleted = false
  AND rack_id IS NOT NULL
  AND bin_id IS NOT NULL
GROUP BY sku_id, warehouse_id, rack_id, bin_id
HAVING COUNT(*) > 1;

-- 4. OUTBOUND PENDING items that cannot be mapped to exactly one legacy batch.
SELECT i.id AS receipt_item_id,
       r.id AS receipt_id,
       r.warehouse_id,
       i.sku_id,
       i.rack_id,
       i.bin_id,
       COUNT(DISTINCT b.id) AS candidate_batch_count,
       ARRAY_AGG(DISTINCT b.id) FILTER (WHERE b.id IS NOT NULL) AS candidate_batch_ids
FROM public.inventory_receipt_items i
JOIN public.inventory_receipts r ON r.id = i.receipt_id
LEFT JOIN public.stock_batches b
       ON b.sku_id = i.sku_id
      AND b.warehouse_id = r.warehouse_id
      AND b.rack_id IS NOT DISTINCT FROM i.rack_id
      AND b.bin_id IS NOT DISTINCT FROM i.bin_id
      AND b.is_deleted = false
WHERE r.type = 'OUTBOUND'
  AND r.status = 'PENDING'
GROUP BY i.id, r.id, r.warehouse_id, i.sku_id, i.rack_id, i.bin_id
HAVING COUNT(DISTINCT b.id) <> 1;

-- 5. Receipt items whose SKU or physical location belongs to another warehouse.
SELECT i.id AS receipt_item_id,
       r.id AS receipt_id,
       r.type,
       r.warehouse_id,
       i.sku_id,
       i.rack_id,
       i.bin_id,
       rack.layout_id,
       layout.warehouse_id AS layout_warehouse_id,
       bin.rack_id AS bin_parent_rack_id
FROM public.inventory_receipt_items i
JOIN public.inventory_receipts r ON r.id = i.receipt_id
LEFT JOIN public.warehouse_racks rack ON rack.id = i.rack_id
LEFT JOIN public.warehouse_bins bin ON bin.id = i.bin_id
LEFT JOIN public.warehouse_layouts layout ON layout.id = rack.layout_id
WHERE i.rack_id IS NULL
   OR i.bin_id IS NULL
   OR rack.id IS NULL
   OR bin.id IS NULL
   OR layout.id IS NULL
   OR layout.warehouse_id IS DISTINCT FROM r.warehouse_id
   OR bin.rack_id IS DISTINCT FROM rack.id;

-- 6. Inventory transactions that reference a missing batch or receipt.
SELECT t.id AS transaction_id,
       t.receipt_id,
       t.batch_id
FROM public.inventory_transactions t
LEFT JOIN public.inventory_receipts r ON r.id = t.receipt_id
LEFT JOIN public.stock_batches b ON b.id = t.batch_id
WHERE r.id IS NULL
   OR b.id IS NULL;

-- 7. OUTBOUND PENDING quantities that exceed the currently available stock.
WITH requested AS (
    SELECT r.id AS receipt_id,
           r.warehouse_id,
           i.sku_id,
           SUM(i.quantity) AS requested_quantity
    FROM public.inventory_receipts r
    JOIN public.inventory_receipt_items i ON i.receipt_id = r.id
    WHERE r.type = 'OUTBOUND'
      AND r.status = 'PENDING'
    GROUP BY r.id, r.warehouse_id, i.sku_id
), available AS (
    SELECT warehouse_id,
           sku_id,
           SUM(quantity) AS available_quantity
    FROM public.stock_batches
    WHERE is_deleted = false
    GROUP BY warehouse_id, sku_id
)
SELECT requested.receipt_id,
       requested.warehouse_id,
       requested.sku_id,
       requested.requested_quantity,
       COALESCE(available.available_quantity, 0) AS available_quantity
FROM requested
LEFT JOIN available
       ON available.warehouse_id = requested.warehouse_id
      AND available.sku_id = requested.sku_id
WHERE requested.requested_quantity > COALESCE(available.available_quantity, 0);
