-- Read-only preflight for 20260909_01_align_warehouse_rental_area_allocation.sql.
-- Run this against the target database before deployment.
-- Every result set is diagnostic; this script does not change data.

-- 1. Active default layouts with invalid dimensions.
SELECT id, warehouse_id, width, length, height
FROM warehouse_layouts
WHERE is_default = TRUE
  AND is_active = TRUE
  AND is_deleted = FALSE
  AND (
      width IS NULL OR length IS NULL OR height IS NULL
      OR width <= 0 OR length <= 0 OR height <= 0
  );

-- 2. Warehouses with more than one active default layout.
SELECT warehouse_id, COUNT(*) AS active_default_layout_count
FROM warehouse_layouts
WHERE is_default = TRUE
  AND is_active = TRUE
  AND is_deleted = FALSE
GROUP BY warehouse_id
HAVING COUNT(*) > 1;

-- 3. Warehouse capacity values that do not match default-layout area.
SELECT w.id AS warehouse_id,
       w.capacity AS stored_capacity_m2,
       ROUND(l.width * l.length, 2) AS expected_capacity_m2,
       l.id AS default_layout_id
FROM warehouses w
JOIN warehouse_layouts l ON l.warehouse_id = w.id
WHERE l.is_default = TRUE
  AND l.is_active = TRUE
  AND l.is_deleted = FALSE
  AND (
      l.width IS NULL OR l.length IS NULL OR l.height IS NULL
      OR l.width <= 0 OR l.length <= 0 OR l.height <= 0
      OR w.capacity IS DISTINCT FROM ROUND(l.width * l.length, 2)
  );

-- 4. Contract area that does not match its stored dimensions.
SELECT id AS contract_id,
       warehouse_id,
       pricing_type,
       leased_width,
       leased_length,
       leased_area_m2
FROM rental_contracts
WHERE leased_width IS NULL OR leased_length IS NULL OR leased_area_m2 IS NULL
   OR leased_width <= 0 OR leased_length <= 0
   OR leased_area_m2 <= 0
   OR leased_area_m2 IS DISTINCT FROM leased_width * leased_length;

-- 5. Fixed contracts whose dimensions differ from the current default layout.
SELECT c.id AS contract_id,
       c.warehouse_id,
       c.leased_width,
       c.leased_length,
       c.leased_height,
       l.width AS default_width,
       l.length AS default_length,
       l.height AS default_height
FROM rental_contracts c
JOIN warehouse_layouts l ON l.warehouse_id = c.warehouse_id
WHERE c.pricing_type = 'FIXED_MONTHLY'
  AND l.is_default = TRUE
  AND l.is_active = TRUE
  AND l.is_deleted = FALSE
  AND (
      c.leased_width IS DISTINCT FROM l.width
      OR c.leased_length IS DISTINCT FROM l.length
      OR c.leased_height IS DISTINCT FROM l.height
  );

-- 6. Partial/negotiated contracts that consume the whole default area or more.
SELECT c.id AS contract_id,
       c.warehouse_id,
       c.pricing_type,
       c.leased_area_m2,
       ROUND(l.width * l.length, 2) AS warehouse_total_area_m2
FROM rental_contracts c
JOIN warehouse_layouts l ON l.warehouse_id = c.warehouse_id
WHERE c.pricing_type IN ('PER_SQUARE_METER_MONTHLY', 'NEGOTIATED')
  AND l.is_default = TRUE
  AND l.is_active = TRUE
  AND l.is_deleted = FALSE
  AND c.leased_area_m2 >= l.width * l.length;

-- 7. Actionable contracts whose overlapping area exceeds the default layout.
-- End dates are inclusive, so each release event is placed on end_date + 1.
WITH active_default_area AS (
    SELECT warehouse_id, MIN(width * length) AS total_area_m2
    FROM warehouse_layouts
    WHERE is_default = TRUE
      AND is_active = TRUE
      AND is_deleted = FALSE
      AND width > 0 AND length > 0 AND height > 0
    GROUP BY warehouse_id
), actionable_contracts AS (
    SELECT id, warehouse_id, start_date, end_date, leased_area_m2
    FROM rental_contracts
    WHERE status IN ('PENDING_TENANT_CONFIRM', 'SCHEDULED', 'ACTIVE')
      AND is_active = TRUE
      AND is_deleted = FALSE
      AND start_date IS NOT NULL
      AND end_date IS NOT NULL
      AND start_date <= end_date
      AND leased_area_m2 > 0
), area_events AS (
    SELECT warehouse_id, start_date AS event_date, leased_area_m2 AS area_delta
    FROM actionable_contracts
    UNION ALL
    SELECT warehouse_id, end_date + 1 AS event_date, -leased_area_m2 AS area_delta
    FROM actionable_contracts
), collapsed_events AS (
    SELECT warehouse_id, event_date, SUM(area_delta) AS area_delta
    FROM area_events
    GROUP BY warehouse_id, event_date
), running_area AS (
    SELECT warehouse_id,
           event_date,
           SUM(area_delta) OVER (
               PARTITION BY warehouse_id
               ORDER BY event_date
               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
           ) AS reserved_area_m2
    FROM collapsed_events
)
SELECT r.warehouse_id,
       r.event_date,
       r.reserved_area_m2,
       a.total_area_m2
FROM running_area r
JOIN active_default_area a ON a.warehouse_id = r.warehouse_id
WHERE r.reserved_area_m2 > a.total_area_m2
ORDER BY r.warehouse_id, r.event_date;
