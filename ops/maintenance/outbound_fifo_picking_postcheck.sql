-- Post-migration verification for 20260831_01_refactor_stock_batches_for_fifo_picking.sql.
-- All count_value results must be zero. The final query is informational:
-- duplicate SKU/location batches are now intentionally allowed.

SELECT 'positive_batch_missing_arrival_date' AS check_name,
       COUNT(*) AS count_value
FROM public.stock_batches
WHERE is_deleted = false
  AND quantity > 0
  AND arrival_date IS NULL;

SELECT 'outbound_pending_missing_batch_or_sequence' AS check_name,
       COUNT(*) AS count_value
FROM public.inventory_receipt_items i
JOIN public.inventory_receipts r ON r.id = i.receipt_id
WHERE r.type = 'OUTBOUND'
  AND r.status = 'PENDING'
  AND (i.stock_batch_id IS NULL OR i.pick_sequence IS NULL OR i.pick_sequence <= 0);

SELECT 'receipt_item_batch_reference_or_location_mismatch' AS check_name,
       COUNT(*) AS count_value
FROM public.inventory_receipt_items i
JOIN public.inventory_receipts r ON r.id = i.receipt_id
JOIN public.stock_batches b ON b.id = i.stock_batch_id
WHERE i.stock_batch_id IS NOT NULL
  AND (
      b.sku_id IS DISTINCT FROM i.sku_id
      OR b.warehouse_id IS DISTINCT FROM r.warehouse_id
      OR b.rack_id IS DISTINCT FROM i.rack_id
      OR b.bin_id IS DISTINCT FROM i.bin_id
  );

SELECT 'orphan_receipt_item_batch_reference' AS check_name,
       COUNT(*) AS count_value
FROM public.inventory_receipt_items i
LEFT JOIN public.stock_batches b ON b.id = i.stock_batch_id
WHERE i.stock_batch_id IS NOT NULL
  AND b.id IS NULL;

SELECT 'invalid_pick_sequence' AS check_name,
       COUNT(*) AS count_value
FROM public.inventory_receipt_items
WHERE pick_sequence IS NOT NULL
  AND pick_sequence <= 0;

SELECT 'legacy_single_batch_unique_index_present' AS check_name,
       COUNT(*) AS count_value
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname = 'ux_stock_batches_sku_location_active';

SELECT sku_id,
       warehouse_id,
       rack_id,
       bin_id,
       COUNT(*) AS batch_count,
       SUM(quantity) AS total_quantity
FROM public.stock_batches
WHERE is_deleted = false
GROUP BY sku_id, warehouse_id, rack_id, bin_id
HAVING COUNT(*) > 1
ORDER BY sku_id, warehouse_id, rack_id, bin_id;
