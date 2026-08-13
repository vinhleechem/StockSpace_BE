# FE Handoff: Tenant-wide SKU and Warehouse-specific Stock

## Business decision

The backend follows **Approach A**:

- `ProductSku` is a tenant-wide product master.
- A SKU is created once by the Tenant and can be selected in every warehouse rented by that Tenant.
- Physical quantity is stored per warehouse through `StockBatch`.
- Example: SKU-1 has 5 units in Thu Duc and 10 units in Minh Tam; the tenant total is 15 units.
- A warehouse must not be added to the SKU creation request.

## Backend changes

- Only a user with `ROLE_TENANT` can call `POST /api/tenant/products/skus`.
- Staff can still read the SKU catalog because they need it when creating inventory receipts.
- Receipt creation now validates:
  - the SKU belongs to the current tenant or is a system SKU;
  - the selected warehouse has an active rental contract for the tenant;
  - a Staff user has an active assignment to the selected warehouse.
- Receipt approval is restricted to the tenant that owns the receipt warehouse.
- Receipt list, detail, export, and stock queries enforce the same warehouse/assignment scope.

No database migration is required for this change. The existing schema already stores the SKU master separately from warehouse-specific `StockBatch` records. Any future data cleanup must be delivered as a separate SQL migration under `ops/migrations`.

## Stock summary response

For `GET /api/tenant/inventory/stock/summary?skuId={skuId}`, `locations` now includes:

```json
{
  "batchId": "...",
  "warehouseId": "...",
  "warehouseName": "Thu Duc Warehouse",
  "rackName": "Rack A",
  "binName": "Bin 01",
  "quantity": 5
}
```

`totalQuantity` is the sum of the returned locations. The FE should group or display locations by `warehouseId`/`warehouseName` when showing the warehouse breakdown.

## Required FE alignment

- Keep SKU selection tenant-wide in inbound/outbound forms; filter the stock destination by the selected warehouse, not by creating duplicate SKUs.
- Display the warehouse name for each stock location. Do not assume a SKU has only one warehouse location.
- Show the **Create SKU** action only to Tenant users. Still show SKU selection to Staff users.
- Handle `403 Forbidden` from SKU creation and warehouse-scoped inventory operations.
- In inventory tables, use the backend field `skuCode` rather than `sku`.
- Use `categoryName` from the SKU response rather than `category` when displaying the category.
- The stock location response currently provides rack/bin and warehouse fields; it does not provide `zoneName`.

## Acceptance scenarios

1. Tenant creates SKU-1 once.
2. Staff assigned to Thu Duc creates an inbound receipt for SKU-1 with quantity 5.
3. Staff assigned to Minh Tam creates an inbound receipt for SKU-1 with quantity 10.
4. Tenant stock summary returns `totalQuantity = 15` and locations that identify Thu Duc = 5 and Minh Tam = 10.
5. Staff without an active assignment cannot create or read receipts/stock for that warehouse.
6. A SKU owned by another tenant cannot be used in a receipt, even if its UUID is submitted directly.
