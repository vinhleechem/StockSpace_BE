# Warehouse Search Filters API

## Scope

This document describes the public warehouse search contract after Plan 06.
The existing endpoint is extended with structured location, warehouse type and
capacity filters. Existing requests without the new parameters remain valid.

## Public search endpoint

```http
GET /api/warehouses
```

All parameters are optional unless stated otherwise. The endpoint is public
and returns only warehouses that are active, not deleted, in `AVAILABLE`
listing status, published and not past `visibleUntil`. Verification is an
optional filter, not a public visibility gate.

### Query parameters

| Parameter | Type | Meaning |
|---|---|---|
| `keyword` | string | Searches warehouse name, free-form address, description and warehouse type name. |
| `provinceCode` | string | Exact match against normalized province code. |
| `districtCode` | string | Exact match against normalized district code. If province is also supplied, both values must match the same warehouse. |
| `warehouseTypeId` | UUID | Exact warehouse type identifier. Get available types from `GET /api/warehouses/types`. |
| `minCapacity` | decimal | Inclusive lower bound for `capacity`. |
| `maxCapacity` | decimal | Inclusive upper bound for `capacity`. |
| `minRentalPrice` | decimal | Inclusive lower bound for the published `rentalPrice`. |
| `maxRentalPrice` | decimal | Inclusive upper bound for the published `rentalPrice`. |
| `isVerified` | boolean | Optional verification filter. Omit it for both verified and unverified public results; send `true` or `false` to filter explicitly. |
| `minPrice` | decimal | Deprecated compatibility alias for `minRentalPrice`. |
| `maxPrice` | decimal | Deprecated compatibility alias for `maxRentalPrice`. |
| `page` | integer | Zero-based page number. Default `0`, maximum `10000`. |
| `size` | integer | Page size. Default `10`, maximum `50`. |
| `sortBy` | string | One of `createdAt`, `updatedAt`, `name`, `rentalPrice`, `capacity`. Default `createdAt`. |
| `sortDir` | string | `asc` or `desc`. Default `desc`. |

Capacity and price values must be non-negative, have at most two decimal
places, and be no greater than `9999999999999.99`. `minCapacity` cannot be
greater than `maxCapacity`; the same rule applies to the rental price range.
Blank location codes are treated as not supplied.

### Price alias compatibility

`minRentalPrice` and `maxRentalPrice` are the preferred names. `minPrice` and
`maxPrice` remain available for one compatibility release. If both a legacy
alias and its preferred field are present, the preferred field wins.

Example:

```http
GET /api/warehouses?provinceCode=79&districtCode=760&warehouseTypeId=TYPE_UUID&minCapacity=50&maxCapacity=200&minRentalPrice=1000000&maxRentalPrice=5000000&page=0&size=20&sortBy=capacity&sortDir=asc
```

The frontend should send filters to the API instead of filtering the current
page in memory. A response page is not the complete result set.

## Response shape

Successful responses use the standard envelope:

```json
{
  "success": true,
  "message": "Lấy danh sách kho thành công",
  "data": {
    "content": [
      {
        "id": "WAREHOUSE_UUID",
        "name": "Warehouse name",
        "address": "Free-form display address",
        "provinceCode": "79",
        "provinceName": "Thành phố Hồ Chí Minh",
        "districtCode": "760",
        "districtName": "Quận 9",
        "capacity": 200.00,
        "rentalPrice": 5000000.00,
        "rentalPricingType": "FIXED_MONTHLY",
        "typeId": "TYPE_UUID",
        "typeName": "Kho khô",
        "isVerified": true,
        "status": "AVAILABLE",
        "publishedAt": "2026-08-27T10:00:00",
        "visibleUntil": "2026-09-26T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "last": true
  }
}
```

The actual response also contains the existing warehouse image, policy and
publication fields. The new location properties are nullable for legacy rows.
An approved unverified warehouse may therefore have `isVerified: false` in a
public response.

## Pricing display rules

`rentalPrice` is the published listing price, not a contract's final agreed
rent. The frontend must use `rentalPricingType` when displaying its unit:

- `FIXED_MONTHLY`: `rentalPrice` is the published monthly warehouse price.
- `PER_SQUARE_METER_MONTHLY`: `rentalPrice` is the published monthly price per
  square metre.
- `NEGOTIATED`: `rentalPrice` is `null`; do not render it as zero and do not
  include it in a numeric price-range result.

When no price range is supplied, negotiated listings are eligible for the
public result. When either price bound is supplied, only listings with a
numeric `rentalPrice` inside the range are eligible.

## Owner location payload

The normalized location fields are optional to preserve old clients. New or
updated owner forms should send the selected administrative code and its
canonical display name together:

```json
{
  "address": "12 Example Street, Ward 1",
  "provinceCode": "79",
  "provinceName": "Thành phố Hồ Chí Minh",
  "districtCode": "760",
  "districtName": "Quận 9"
}
```

Rules:

- A province code and province name must be supplied together.
- A district code and district name must be supplied together.
- A district requires a province in the same payload/effective record.
- The backend does not parse free-form `address` during search.
- When an owner changes `address` without sending structured location fields,
  the old normalized location is cleared to prevent stale filter results.

The create endpoint remains:

```http
POST /api/owner/warehouses
Content-Type: multipart/form-data
```

Put the JSON above in the existing `request` part. The update endpoint remains:

```http
PUT /api/owner/warehouses/{warehouseId}
Content-Type: application/json
```

The migration backfills `provinceCode = "79"` and the HCM province name only
for legacy addresses with the deterministic `Thành phố Hồ Chí Minh` suffix.
It intentionally does not guess district values from free-form text. Existing
legacy rows can therefore have null district fields until the owner updates
them with structured values.

## Migration and production verification

The backend migration is:

```text
ops/migrations/20260827_02_add_warehouse_search_location.sql
```

It is applied by the existing `ops/run-migrations.sh` runner and creates the
following indexes:

- `idx_warehouses_province_code`
- `idx_warehouses_district_code`
- `idx_warehouses_province_district`

Before deployment, run the migration runner in dry-run mode according to the
existing operations procedure. After applying it, verify the columns and
indexes on the target database. For a production-sized dataset, inspect the
planner with a representative read-only query, for example:

```sql
EXPLAIN (FORMAT TEXT)
SELECT w.id
FROM warehouses w
WHERE w.is_active = true
  AND w.is_deleted = false
  AND w.status = 'AVAILABLE'
  AND w.published_at IS NOT NULL
  AND w.visible_until IS NOT NULL
  AND w.visible_until >= CURRENT_TIMESTAMP
  AND w.province_code = '79'
  AND w.district_code = '760'
  AND w.capacity BETWEEN 50 AND 200;
```

The planner may still choose a sequential scan for a small table; that is not
automatically a defect. The check is to confirm that the plan is reasonable
for the real data volume and that the new columns/indexes are available.

## Out of scope

This change does not add geospatial radius search, full-text search,
storage-condition taxonomy, AI search filters or frontend source changes.
