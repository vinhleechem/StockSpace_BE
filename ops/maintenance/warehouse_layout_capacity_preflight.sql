-- Read-only preflight. Run this against the target database before deploying
-- the meter geometry migration or cleaning legacy rows.

-- 1. Layouts with missing or non-positive real dimensions.
SELECT id, warehouse_id, tenant_id, width, length, height
FROM warehouse_layouts
WHERE width IS NULL OR length IS NULL OR height IS NULL
   OR width <= 0 OR length <= 0 OR height <= 0;

-- 2. Racks with invalid scalar geometry/capacity.
SELECT id, layout_id, name, code, coordinate_x, coordinate_y, position_z,
       width, length, height, max_weight, max_volume
FROM warehouse_racks
WHERE coordinate_x IS NULL OR coordinate_y IS NULL OR position_z IS NULL
   OR width IS NULL OR length IS NULL OR height IS NULL
   OR coordinate_x < 0 OR coordinate_y < 0 OR position_z < 0
   OR width <= 0 OR length <= 0 OR height <= 0
   OR max_weight < 0 OR max_volume < 0
   OR (max_volume > 0 AND max_volume > width * length * height);

-- 3. Bins with invalid scalar geometry/capacity.
SELECT id, rack_id, name, code, coordinate_x, coordinate_y, position_z,
       width, length, height, max_weight, max_volume
FROM warehouse_bins
WHERE coordinate_x IS NULL OR coordinate_y IS NULL OR position_z IS NULL
   OR width IS NULL OR length IS NULL OR height IS NULL
   OR coordinate_x < 0 OR coordinate_y < 0 OR position_z < 0
   OR width <= 0 OR length <= 0 OR height <= 0
   OR max_weight < 0 OR max_volume < 0
   OR (max_volume > 0 AND max_volume > width * length * height);

-- 4. Rack bounds against the parent layout (rotation 0/180 only).
SELECT r.id, r.name, l.id AS layout_id
FROM warehouse_racks r
JOIN warehouse_layouts l ON l.id = r.layout_id
WHERE r.coordinate_x + r.width > l.width
   OR r.coordinate_y + r.length > l.length
   OR r.position_z + r.height > l.height;

-- 5. Bin bounds against the parent rack. Bin coordinates are rack-local.
SELECT b.id, b.name, r.id AS rack_id
FROM warehouse_bins b
JOIN warehouse_racks r ON r.id = b.rack_id
WHERE b.coordinate_x + b.width > r.width
   OR b.coordinate_y + b.length > r.length
   OR b.position_z + b.height > r.height;

-- 6. Duplicate logical layouts/codes that would prevent safe unique indexes.
SELECT warehouse_id, COUNT(*) AS default_layout_count
FROM warehouse_layouts
WHERE is_default = true AND is_deleted = false
GROUP BY warehouse_id
HAVING COUNT(*) > 1;

SELECT warehouse_id, tenant_id, COUNT(*) AS tenant_layout_count
FROM warehouse_layouts
WHERE is_default = false AND is_deleted = false
GROUP BY warehouse_id, tenant_id
HAVING COUNT(*) > 1;

SELECT layout_id, code, COUNT(*) AS rack_code_count
FROM warehouse_racks
WHERE is_deleted = false
GROUP BY layout_id, code
HAVING COUNT(*) > 1;

SELECT rack_id, code, COUNT(*) AS bin_code_count
FROM warehouse_bins
WHERE is_deleted = false
GROUP BY rack_id, code
HAVING COUNT(*) > 1;

-- 7. Duplicate stock rows for one physical location and SKU.
SELECT sku_id, warehouse_id, rack_id, bin_id, COUNT(*) AS batch_count,
       SUM(quantity) AS total_quantity
FROM stock_batches
WHERE is_deleted = false
GROUP BY sku_id, warehouse_id, rack_id, bin_id
HAVING COUNT(*) > 1;

-- 8. Existing stock whose SKU cannot be converted to physical load.
SELECT DISTINCT s.id, s.sku_code, s.name, s.unit_weight_kg, s.unit_volume_m3
FROM product_skus s
JOIN stock_batches b ON b.sku_id = s.id
WHERE b.is_deleted = false
  AND (s.unit_weight_kg IS NULL OR s.unit_weight_kg <= 0
    OR s.unit_volume_m3 IS NULL OR s.unit_volume_m3 <= 0);

-- 9. Legacy zero capacities. Current application treats NULL and 0 as unlimited.
-- Review these rows before normalizing 0 to NULL.
SELECT 'rack' AS entity_type, id, name, max_weight, max_volume
FROM warehouse_racks
WHERE max_weight = 0 OR max_volume = 0
UNION ALL
SELECT 'bin' AS entity_type, id, name, max_weight, max_volume
FROM warehouse_bins
WHERE max_weight = 0 OR max_volume = 0;
