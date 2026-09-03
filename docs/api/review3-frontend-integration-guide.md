# Review 3 Frontend Integration Guide

This guide is the backend contract for the Review 3 remediation features. It is
written from the release candidate through commit `8f6888b` and the controller/DTO
source in this repository. The frontend may use it as implementation input, but
the backend remains the final authority for authorization, status transitions,
capacity and stock quantities.

## 1. Common rules

### Authentication and response envelope

Send the access token on every authenticated request:

```http
Authorization: Bearer <access-token>
Content-Type: application/json
```

Successful responses use:

```json
{
  "success": true,
  "message": "...",
  "data": {}
}
```

Error responses use the same envelope. `code` is present when the backend maps
the error to a stable `ErrorCode`; some validation errors may contain only
`message`:

```json
{
  "success": false,
  "code": "SOME_ERROR_CODE",
  "message": "Human-readable explanation"
}
```

Use UUID values exactly as returned by the backend. Do not manufacture IDs on
the client. Page indexes are zero-based.

### Warehouse scope

For tenant WMS APIs, always send the warehouse selected by the user. The backend
resolves the tenant from the authenticated context and re-checks warehouse,
contract, subscription and staff assignment rules in the service layer. A
frontend filter is not an authorization check.

`null` or `0` capacity means unlimited in the current backend convention. A
positive `maxWeight`/`maxVolume` is a real limit. Do not convert these values to
zero in the UI.

## 2. Layout read and tenant customization

### 2.1 Read the tenant layout

```http
GET /api/tenant/warehouses/{warehouseId}/layout
```

Permission: `WAREHOUSE_LAYOUT_TENANT_MANAGE`.

For a staff read-only layout use instead:

```http
GET /api/staff/warehouses/{warehouseId}/layout
```

Permission: `INVENTORY_READ`; the staff must have an active assignment and
active contract access.

The public/guest layout endpoint is:

```http
GET /api/warehouses/{warehouseId}/layout
```

The response data has this shape:

```json
{
  "id": "layout-uuid",
  "warehouseId": "warehouse-uuid",
  "tenantId": "tenant-uuid",
  "default": false,
  "width": 40.0,
  "length": 30.0,
  "height": 8.0,
  "totalRacks": 2,
  "totalBins": 8,
  "occupiedBins": 1,
  "emptyBins": 7,
  "racks": [
    {
      "id": "rack-uuid",
      "layoutId": "layout-uuid",
      "name": "Rack A1",
      "code": "RACK-A1",
      "maxWeight": 1000.0,
      "maxVolume": 120.0,
      "coordinateX": 0.0,
      "coordinateY": 0.0,
      "positionZ": 0.0,
      "rotation": 0,
      "width": 10.0,
      "length": 5.0,
      "height": 6.0,
      "occupiedPositions": [],
      "bins": [
        {
          "id": "bin-uuid",
          "rackId": "rack-uuid",
          "name": "Bin A1-01",
          "code": "BIN-A1-01",
          "maxWeight": 100.0,
          "maxVolume": 20.0,
          "shelfLevel": 0,
          "coordinateX": 0.0,
          "coordinateY": 0.0,
          "positionZ": 0.0,
          "width": 2.0,
          "length": 2.0,
          "height": 2.0,
          "occupied": false,
          "occupiedPositions": []
        }
      ]
    }
  ],
  "positions": []
}
```

The JSON property generated for the Java boolean fields is `default` and
`occupied` in the current Jackson configuration. The frontend should tolerate
the exact property names emitted by the deployed OpenAPI response and must not
infer occupied quantity from the geometry response.

### 2.2 Save the tenant layout

```http
PUT /api/tenant/warehouses/{warehouseId}/layout
```

Permission: `WAREHOUSE_LAYOUT_TENANT_MANAGE`.

Request:

```json
{
  "width": 40.0,
  "length": 30.0,
  "height": 8.0,
  "racks": [
    {
      "id": "rack-uuid-or-null-for-new",
      "name": "Rack A1",
      "code": "RACK-A1",
      "maxWeight": 1000.0,
      "maxVolume": 120.0,
      "coordinateX": 0.0,
      "coordinateY": 0.0,
      "positionZ": 0.0,
      "rotation": 0,
      "width": 10.0,
      "length": 5.0,
      "height": 6.0,
      "bins": [
        {
          "id": "bin-uuid-or-null-for-new",
          "shelfLevel": 0,
          "name": "Bin A1-01",
          "code": "BIN-A1-01",
          "maxWeight": 100.0,
          "maxVolume": 20.0,
          "coordinateX": 0.0,
          "coordinateY": 0.0,
          "positionZ": 0.0,
          "width": 2.0,
          "length": 2.0,
          "height": 2.0
        }
      ]
    }
  ],
  "positions": []
}
```

All layout/rack/bin dimensions are meters. Width, length and height must be
positive; coordinates must be non-negative. A rack must fit in its parent
layout and a bin must fit in its parent rack after rotation is applied. Existing
stock, contract lease dimensions and tenant/owner scope are checked by the
backend. A rejected request must be shown as an error; do not silently trim
dimensions or coordinates on the client.

## 3. Capacity metrics

### 3.1 Endpoint

```http
GET /api/tenant/warehouses/{warehouseId}/layout/capacity
```

Permission: `INVENTORY_READ`.

This is the authoritative read model for current physical load. It is read-only
and does not reserve capacity or change stock.

Response `data`:

```json
{
  "warehouseId": "warehouse-uuid",
  "warehouseName": "Warehouse A",
  "layoutId": "layout-uuid",
  "racks": [
    {
      "rackId": "rack-uuid",
      "rackName": "Rack A1",
      "currentWeightKg": 25.0,
      "currentVolumeM3": 4.5,
      "maxWeightKg": 1000.0,
      "maxVolumeM3": 120.0,
      "remainingWeightKg": 975.0,
      "remainingVolumeM3": 115.5,
      "weightUtilizationPercent": 2.5,
      "volumeUtilizationPercent": 3.75,
      "capacityStatus": "AVAILABLE",
      "storedSkus": [],
      "bins": [
        {
          "binId": "bin-uuid",
          "binName": "Bin A1-01",
          "currentWeightKg": 25.0,
          "currentVolumeM3": 4.5,
          "maxWeightKg": 100.0,
          "maxVolumeM3": 20.0,
          "remainingWeightKg": 75.0,
          "remainingVolumeM3": 15.5,
          "weightUtilizationPercent": 25.0,
          "volumeUtilizationPercent": 22.5,
          "capacityStatus": "AVAILABLE",
          "storedSkus": [
            {
              "skuId": "sku-uuid",
              "skuCode": "SKU-001",
              "skuName": "Product 1",
              "quantity": 5,
              "weightKg": 25.0,
              "volumeM3": 4.5
            }
          ]
        }
      ]
    }
  ]
}
```

`capacityStatus` is one of `EMPTY`, `AVAILABLE`, `FULL` or `OVER_CAPACITY`.
`remainingWeightKg` and `remainingVolumeM3` are `null` when the corresponding
capacity is unlimited. Percentages are backend-calculated values; do not
recalculate them from rounded display values.

## 4. Stock lookup and receiving

### 4.1 Stock by warehouse

```http
GET /api/tenant/inventory/stock?warehouseId={warehouseId}&page=0&size=20
GET /api/tenant/inventory/stock/overview?warehouseId={warehouseId}&page=0&size=20
```

Permission: `INVENTORY_READ`.

The first endpoint returns stock batches with `id`, SKU data, warehouse/rack/bin
data, `quantity`, `arrivalDate`, `createdAt` and `updatedAt`. The overview is a
product-level paged projection. Use the selected warehouse on both calls.

For one SKU across the tenant's accessible warehouses:

```http
GET /api/tenant/inventory/stock/sku/{skuId}
GET /api/tenant/inventory/stock/summary?skuId={skuId}
```

The summary contains `skuId`, `skuCode`, `skuName`, `uomSymbol`, `uomName`,
`totalQuantity` and `locations[]`. Each location has `batchId`, `warehouseId`,
`warehouseName`, `rackName`, `binName` and `quantity`. Use this endpoint when a
SKU overview must show quantities by warehouse; use the warehouse-filtered
endpoints for a single-warehouse screen.

### 4.2 Create and approve a receipt

```http
POST /api/tenant/inventory/receipts
PATCH /api/tenant/inventory/receipts/{receiptId}/approve
PATCH /api/tenant/inventory/receipts/{receiptId}/reject
```

Create permission: `INBOUND_CREATE` or `OUTBOUND_CREATE` according to the
request type. Approve/reject permission: `INVENTORY_UPDATE`.

Create request:

```json
{
  "warehouseId": "warehouse-uuid",
  "type": "INBOUND",
  "signatureData": "optional-signature",
  "senderName": "Công ty gửi hàng",
  "receiverName": null,
  "items": [
    {
      "skuId": "sku-uuid",
      "quantity": 10,
      "rackId": "rack-uuid",
      "binId": "bin-uuid",
      "note": "optional"
    }
  ]
}
```

`senderName` is the sending organization/place shown for an `INBOUND` receipt;
`receiverName` is the receiving organization/place shown for an `OUTBOUND`
receipt. Both fields are optional and limited to 255 characters so existing and
system-generated receipts remain compatible.

`type` is `INBOUND` or `OUTBOUND`. Every item requires a positive integer
`quantity`, `skuId`, `rackId` and `binId`. New receipts start at
`PENDING`; stock changes only when the receipt is approved. Capacity is checked
again during the mutation, so a previously displayed suggestion can become
stale.

Receipt response fields are `id`, `warehouseId`, `warehouseName`, creator data,
`type`, `signatureData`, `senderName`, `receiverName`, `status`, `rejectReason`,
`items`, `createdAt` and
`updatedAt`. Receipt status is `PENDING`, `APPROVED`, `REJECTED` or `CANCELLED`.

## 5. Put-away suggestions

### 5.1 Suggestion endpoint

```http
POST /api/tenant/inventory/putaway/suggestions
```

Permission: `INVENTORY_READ`.

Request:

```json
{
  "warehouseId": "warehouse-uuid",
  "context": "INBOUND",
  "items": [
    {
      "skuId": "sku-uuid",
      "quantity": 20
    }
  ]
}
```

`context` is `INBOUND` or `TRANSFER_RECEIVE`. Each item needs a positive
integer quantity. The response data contains `warehouseId`, `layoutId`,
`context` and `items[]`:

```json
{
  "warehouseId": "warehouse-uuid",
  "layoutId": "layout-uuid",
  "context": "INBOUND",
  "items": [
    {
      "skuId": "sku-uuid",
      "skuCode": "SKU-001",
      "skuName": "Product 1",
      "requestedQuantity": 20,
      "allocations": [
        {
          "rackId": "rack-uuid",
          "binId": "bin-uuid",
          "quantity": 20,
          "score": 1000,
          "reasons": ["Bin can hold the remaining quantity"],
          "capacity": {
            "rack": {
              "locationId": "rack-uuid",
              "name": "Rack A1",
              "currentWeightKg": 20.0,
              "currentVolumeM3": 4.5,
              "maxWeightKg": 1000.0,
              "maxVolumeM3": 120.0,
              "remainingWeightKg": 980.0,
              "remainingVolumeM3": 115.5
            },
            "bin": {
              "locationId": "bin-uuid",
              "name": "Bin A1-01",
              "currentWeightKg": 2.0,
              "currentVolumeM3": 0.5,
              "maxWeightKg": 100.0,
              "maxVolumeM3": 20.0,
              "remainingWeightKg": 98.0,
              "remainingVolumeM3": 19.5
            }
          }
        }
      ],
      "unallocatedQuantity": 0,
      "warning": null
    }
  ]
}
```

For every item:

```text
sum(allocation.quantity) + unallocatedQuantity = requestedQuantity
```

The frontend must display a warning when `unallocatedQuantity > 0`, and must
not imply that all quantity was placed. Suggestions are deterministic and
capacity-aware, but are not reservations. After the user confirms or overrides
the locations, call the normal receipt creation API for inbound or the transfer
receive API for a transfer. Do not create stock merely because this endpoint
returned `200`.

## 6. Stock transfer between two warehouses of the same tenant

### 6.1 Lifecycle

```text
PENDING --approve-dispatch--> IN_TRANSIT --receive--> COMPLETED
   |                              |
   +--reject----------------> REJECTED
   +--cancel----------------> CANCELLED
```

There is no `APPROVED` transfer state. Dispatch deducts source stock; receive
adds destination stock. Source and destination layouts are independent. Never
map a source bin directly to a destination bin.

### 6.2 Create

```http
POST /api/tenant/inventory/transfers
```

Permission: `INVENTORY_CREATE`.

Request:

```json
{
  "sourceWarehouseId": "source-warehouse-uuid",
  "destinationWarehouseId": "destination-warehouse-uuid",
  "note": "optional",
  "items": [
    {
      "skuId": "sku-uuid",
      "requestedQuantity": 5,
      "sourceAllocations": [
        {
          "sourceStockBatchId": "batch-uuid",
          "sourceRackId": "source-rack-uuid",
          "sourceBinId": "source-bin-uuid",
          "quantity": 5
        }
      ]
    }
  ]
}
```

Source and destination must be different warehouses with active access for the
same tenant. Each SKU item must have at least one source allocation, and the
allocation total must match the requested quantity. A new transfer is
`PENDING`.

### 6.3 Query and state actions

```http
GET /api/tenant/inventory/transfers?sourceWarehouseId={id}&destinationWarehouseId={id}&status=PENDING&page=0&size=10
GET /api/tenant/inventory/transfers/{transferId}
PATCH /api/tenant/inventory/transfers/{transferId}/approve-dispatch
POST /api/tenant/inventory/transfers/{transferId}/receive
PATCH /api/tenant/inventory/transfers/{transferId}/reject
PATCH /api/tenant/inventory/transfers/{transferId}/cancel
```

List/detail permission: `INVENTORY_READ`. State-changing actions require
`INVENTORY_UPDATE`. The service also requires the actor and current state to be
valid; Staff cannot approve, receive, reject or cancel a transfer.

Receive request:

```json
{
  "destinationAllocations": [
    {
      "itemId": "transfer-item-uuid",
      "destinationRackId": "destination-rack-uuid",
      "destinationBinId": "destination-bin-uuid",
      "quantity": 5
    }
  ]
}
```

Reject/cancel request:

```json
{
  "reason": "Business reason"
}
```

`StockTransferResponse` contains `id`, `status`, source/destination warehouse
summary, note, items, actor fields, decision reason, timestamps and the linked
`outboundReceiptId`/`inboundReceiptId` when generated. Items contain SKU data,
requested quantity, source allocations and destination allocations.

## 7. Warehouse search

### 7.1 Public search

```http
GET /api/warehouses
```

The current query names are:

| Parameter | Meaning |
|---|---|
| `keyword` | Text search |
| `minRentalPrice`, `maxRentalPrice` | Current rental price range |
| `minPrice`, `maxPrice` | Compatibility aliases; use only when integrating an older client |
| `minCapacity`, `maxCapacity` | Capacity range |
| `provinceCode`, `districtCode` | Structured location filters |
| `warehouseTypeId` | Warehouse type filter |
| `isVerified` | Optional verification filter. Omit it for all public listings; `true` and `false` are both supported. |
| `page`, `size` | Zero-based pagination; size is limited to 50 |
| `sortBy` | `createdAt`, `updatedAt`, `name`, `rentalPrice`, `capacity` |
| `sortDir` | `asc` or `desc` |

Example:

```http
GET /api/warehouses?provinceCode=HCM&districtCode=Q9&warehouseTypeId={typeId}&minCapacity=100&maxCapacity=1000&minRentalPrice=5000000&maxRentalPrice=20000000&page=0&size=10&sortBy=capacity&sortDir=desc
```

Use `minRentalPrice`/`maxRentalPrice` in new code. If both a current name and
its legacy alias are sent, the current name wins. Filter values must be
non-negative and use at most two decimal places; `min` must not exceed `max`.

### 7.2 Related public endpoints

```http
GET /api/warehouses/{warehouseId}
GET /api/warehouses/{warehouseId}/owner-contact   # authenticated
GET /api/warehouses/types
```

Do not display owner contact to a guest; the contact endpoint requires an
authenticated user.

## 8. Staff operations and access

### 8.1 Staff self-service

```http
GET /api/staff/my-work-history
GET /api/staff/operations?warehouseId={id}&type=RECEIPT&status=PENDING&page=0&size=20
GET /api/staff/warehouses/{warehouseId}/layout
```

The operation endpoint permission is `STAFF_WORK_HISTORY_READ`; layout uses
`INVENTORY_READ`. `type` is `RECEIPT`, `AUDIT` or `TRANSFER`. Without a type,
all three are returned. With no status, the backend returns pending work:

- Receipt: `PENDING`.
- Audit: `PENDING`, `SUBMITTED`.
- Transfer: `PENDING`, `IN_TRANSIT`.

Each operation row contains `operationType`, `operationId`, warehouse fields,
optional source/destination fields, `status`, `createdAt` and
`allowedActions`. This is a read-only projection; it is not a `StaffTask`
entity. Route detail/actions to the owning Receipt, Audit or Transfer API.

### 8.2 Tenant staff administration

```http
POST /api/tenant/staffs/invite
GET /api/tenant/staffs?keyword=&page=0&size=10
DELETE /api/tenant/staffs/{memberId}
POST /api/tenant/staffs/{staffUserId}/warehouses
GET /api/tenant/staffs/{staffUserId}/warehouses
DELETE /api/tenant/staffs/assignments/{assignmentId}
```

Permission: `STAFF_MANAGE`.

Invite request:

```json
{
  "email": "staff@example.com",
  "fullName": "Staff User",
  "phone": "+84901234567"
}
```

Assign request:

```json
{
  "warehouseId": "warehouse-uuid",
  "customTitle": "Warehouse operator",
  "notes": "optional"
}
```

An assignment has status `ACTIVE` or a historical status such as `REVOKED`.
After revoke/remove, the next backend request must be treated as unauthorized
for that warehouse. Do not use the whole work-history list as the active
warehouse selector; filter to active assignments and let the backend re-check
contract access.

## 9. Inventory audit

### 9.1 Lifecycle and permissions

```text
PENDING --submit--> SUBMITTED --approve--> APPROVED
   |                    |
   +------reject------> REJECTED
```

The actual enum is `PENDING`, `SUBMITTED`, `APPROVED`, `REJECTED`. Audit
controller permission: `INVENTORY_AUDIT_MANAGE`. Approval is the boundary that
reconciles stock; creating or submitting an audit does not change stock.

### 9.2 Endpoints and payloads

```http
POST /api/tenant/inventory/audits
GET /api/tenant/inventory/audits?warehouseId={id}&page=0&size=10
GET /api/tenant/inventory/audits/{auditId}
POST /api/tenant/inventory/audits/{auditId}/submit
PATCH /api/tenant/inventory/audits/{auditId}/approve
PATCH /api/tenant/inventory/audits/{auditId}/reject
```

Create request:

```json
{
  "warehouseId": "warehouse-uuid",
  "note": "optional"
}
```

Submit request:

```json
{
  "items": [
    {
      "batchId": "batch-uuid",
      "actualQuantity": 8,
      "note": "optional"
    }
  ]
}
```

Reject may send an optional body:

```json
{
  "reason": "Recount required"
}
```

An audit response contains audit identity, warehouse, `status`, note, requester
and approver, timestamps and items. Each item contains `batchId`, SKU/UOM data,
rack/bin names, `expectedQuantity`, nullable `actualQuantity`, nullable
`discrepancy` and note. The frontend should pass the selected `warehouseId` to
the list request when showing one warehouse, rather than filtering a tenant-wide
list only on the client.

## 10. Error and stale-data handling

| Situation | Expected result | Frontend action |
|---|---|---|
| Missing/invalid UUID, enum or required field | `400` validation error | Keep form data, show field/message error |
| Warehouse/SKU/layout/rack/bin/batch not found | `404` with mapped code when available | Reload the selected warehouse data |
| No active contract, assignment or permission | `403` | Hide/disable the action and refresh access context |
| Inactive/expired subscription where required | `403` | Explain that the WMS subscription must be active |
| Invalid state transition | `400` or `409` depending on service path | Reload the resource and render its current status |
| Capacity or stock changed after a suggestion | `400` or `409` | Reload capacity, request a new suggestion and ask for confirmation again |
| Duplicate submit/approve caused by retry | Existing idempotency/state guard | Do not issue blind retries; reload the resource |

Do not treat an HTTP success from a read endpoint as proof that a later mutation
will succeed. Do not silently retry old rack/bin allocations after a stale-data
error.

## 11. Frontend implementation checklist

- [ ] Keep a selected `warehouseId` in each tenant WMS screen.
- [ ] Use the capacity endpoint for authoritative weight/volume/utilization data.
- [ ] Add clients for capacity, put-away, transfer and staff operations before
      claiming those screens are integrated.
- [ ] Render real status enums and use action metadata only as a display hint;
      backend authorization is final.
- [ ] For put-away, show partial allocation and require confirmation before the
      existing receipt/transfer mutation.
- [ ] For transfer receive, use destination rack/bin IDs from the destination
      layout, never source location IDs.
- [ ] Keep Booking, deposit, dispute and retired handover flows out of the
      current Review 3 WMS demo unless a new approved requirement restores them.
- [ ] Test the same SKU in two warehouses and verify that single-warehouse
      screens do not show the tenant-wide sum.

## 12. Listing publication approval and inspection independence

This section is the authoritative frontend contract for the listing flow.
Listing publication and warehouse inspection are separate workflows:
`isVerified` is a badge/filter value and is not a prerequisite for purchasing
a listing package or for public visibility after Admin publication approval.

### 12.1 Owner flow

The create flow has two consecutive requests. Creating the warehouse stores
warehouse information only; it does not charge the wallet and does not create
the default layout automatically.

1. `POST /api/owner/warehouses` creates the warehouse.
2. `PUT /api/owner/warehouses/{warehouseId}/layout` saves the owner's default
   layout. The owner must complete this successfully before selecting a
   listing package.
3. `GET /api/listing-packages` loads active packages. Do not hardcode price or
   duration.
4. `POST /api/owner/warehouses/{warehouseId}/publications` charges the wallet
   and creates a paid order waiting for Admin approval.
5. Admin approves or rejects the paid publication order.
6. After approval, refetch the owner warehouse and public warehouse queries.

The layout request uses the existing bulk payload. Dimensions are meters and
must be positive; rack/bin coordinates are non-negative and each child must
fit inside its parent.

```json
{
  "width": 40.0,
  "length": 30.0,
  "height": 8.0,
  "racks": [],
  "positions": []
}
```

### 12.2 Listing package and purchase APIs

```http
GET /api/listing-packages
GET /api/listing-packages/{packageId}
POST /api/owner/warehouses/{warehouseId}/publications
GET /api/owner/warehouses/{warehouseId}/publications
```

Purchase request:

```json
{
  "listingPackageId": "package-uuid"
}
```

For a newly created warehouse, the successful payment response is `201` and
has `status = PENDING_APPROVAL`; `periodStart` and `periodEnd` are intentionally
`null` until Admin approval:

```json
{
  "id": "listing-order-uuid",
  "warehouseId": "warehouse-uuid",
  "listingPackageId": "package-uuid",
  "listingPackageName": "30-day listing",
  "transactionId": "listing-fee-transaction-uuid",
  "refundTransactionId": null,
  "status": "PENDING_APPROVAL",
  "durationDays": 30,
  "price": 120000.00,
  "periodStart": null,
  "periodEnd": null,
  "createdAt": "2026-08-28T10:00:00"
}
```

After approval, the order becomes `ACTIVATED`, the period is populated from
the approval timestamp, and the warehouse becomes `AVAILABLE`. A renewal for
an already approved warehouse remains `ACTIVATED` and extends from the current
`visibleUntil`; it does not require inspection or another Admin approval.

The history response uses the same fields. `refundTransactionId` is present
when an Admin rejection refunded the listing fee. Do not infer a refund from
the wallet balance alone.

### 12.3 Admin approval/rejection APIs

```http
POST /api/admin/warehouses/{warehouseId}/approve
POST /api/admin/warehouses/{warehouseId}/reject
```

Reject request body is optional:

```json
{
  "reason": "Required documents are incomplete"
}
```

Admin can approve/reject only a paid pending listing order. On approval the
warehouse is public only if it is active, not deleted, `AVAILABLE`, and its
publication period has not expired. On rejection the fee is refunded exactly
once, the order becomes `REFUNDED`, and the warehouse is not public.

### 12.4 Owner publication state and action flags

Owner/admin warehouse responses include:

```json
{
  "status": "AVAILABLE",
  "publicationStatus": "PUBLISHED",
  "canPublish": false,
  "canRenew": true,
  "currentListingOrderId": "listing-order-uuid",
  "currentListingOrderStatus": "ACTIVATED"
}
```

`publicationStatus` is one of `DRAFT`, `PENDING_APPROVAL`, `PUBLISHED`,
`EXPIRED` or `REFUNDED`.

| State | Meaning | `canPublish` | `canRenew` |
|---|---|---:|---:|
| `DRAFT` | No paid pending publication; a first purchase may be started | `true` when warehouse is active and not deleted | `false` |
| `PENDING_APPROVAL` | Payment succeeded and Admin decision is pending | `false` | `false` |
| `PUBLISHED` | Approved publication is within its period | `false` | `true` |
| `EXPIRED` | Approved publication period has ended | `false` | `true` |
| `REFUNDED` | Rejected publication was refunded and has not been resubmitted | `false` | `false` |

After rejection, the owner may edit the warehouse and call:

```http
POST /api/owner/warehouses/{warehouseId}/resubmit
```

This action only moves the rejected warehouse back to a state where the owner
can buy a new package. It does not charge the wallet. The frontend must not
call the old `/verify` endpoint and must not change warehouse status locally.

### 12.5 Public visibility and inspection

Public listing/detail/contact/layout endpoints remain:

```http
GET /api/warehouses
GET /api/warehouses/{warehouseId}
GET /api/warehouses/{warehouseId}/owner-contact
GET /api/warehouses/{warehouseId}/layout
```

The public search accepts optional `isVerified=true` or `false`; omitting the
parameter returns both verified and unverified warehouses that satisfy the
publication predicate. The frontend should render the verification badge from
the response field and must not hide an approved unverified warehouse.

Inspection is requested and displayed separately:

```http
POST /api/owner/inspections?warehouseId={warehouseId}
GET /api/owner/inspections?page=0&size=10
GET /api/inspector/inspections?page=0&size=10
POST /api/inspector/inspections/{inspectionId}/report
```

Do not block the listing package button or public listing on inspection status.
After payment, approval, rejection or resubmission, refetch the affected
warehouse, publication history, wallet and transaction data instead of making
optimistic state assumptions.

### 12.6 Error and UI action map

| Code | UI handling |
|---|---|
| `WAREHOUSE_DEFAULT_LAYOUT_REQUIRED` (`409`) | Keep owner on layout step; do not open package/payment step. |
| `LISTING_PACKAGE_INACTIVE` (`400`) | Reload package catalog and ask the owner to select another active package. |
| `INSUFFICIENT_BALANCE` (`400`) | Keep warehouse/order unchanged and direct the owner to wallet top-up. |
| `LISTING_PUBLICATION_PENDING` (`409`) | Disable duplicate purchase and reload warehouse/history. |
| `LISTING_PAYMENT_REQUIRED` (`409`) | Admin must not retry approval/rejection blindly; reload pending order/payment state. |
| `WAREHOUSE_INVALID_STATUS_TRANSITION` (`400`) | Reload warehouse and render the returned state. |
| `WAREHOUSE_NOT_OWNED` (`403`) | Stop the action; do not retry with a guessed ID. |

Never treat `periodStart: null` on a pending paid order as a failed payment.
Never use local time changes to test server publication expiry.
