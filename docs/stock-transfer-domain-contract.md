# Stock Transfer Domain Contract (Plan 04)

This document locks the Backend contract for the MVP stock transfer flow before the database migration is created. Later implementation commits must follow this document and must not introduce a second stock-mutation path.

## 1. Scope and invariants

The feature moves stock between two warehouses that are both currently rented by the same Tenant.

The source and destination warehouse must be different. The same Tenant must have an `ACTIVE` Contract for both warehouses at the time of create, dispatch, and receive. Every mutating operation requires an `ACTIVE` Subscription. Staff also requires an `ACTIVE` assignment at each warehouse involved in the operation, but Staff cannot approve dispatch, reject/cancel, or receive a transfer.

The SKU must belong to the Tenant. Quantity is an integer greater than zero. A transfer item represents one SKU, and a transfer must not contain the same SKU more than once; multiple physical locations are represented by allocations.

The transfer is the only API that FE calls for this workflow. FE must not create an outbound receipt and an inbound receipt separately for one transfer.

## 2. State machine

```text
PENDING --approve-dispatch--> IN_TRANSIT --receive--> COMPLETED
   |                              |
   +--reject----------------> REJECTED
   +--cancel----------------> CANCELLED
```

| Current state | Action | Actor | Next state | Stock effect |
|---|---|---|---|---|
| `PENDING` | Approve dispatch | Tenant | `IN_TRANSIT` | Subtract source allocations exactly once |
| `PENDING` | Reject | Tenant | `REJECTED` | No stock change |
| `PENDING` | Cancel | Tenant | `CANCELLED` | No stock change |
| `IN_TRANSIT` | Receive | Tenant | `COMPLETED` | Add destination allocations exactly once |
| `IN_TRANSIT` | Cancel/reject | Any actor | No transition | Not allowed in MVP; return `409` |
| `COMPLETED`, `REJECTED`, `CANCELLED` | Any transition | Any actor | No transition | Not allowed |

There is no `APPROVED` transfer state. `IN_TRANSIT` means the source stock has already been deducted and the destination stock has not been added yet. The transfer row and its allocations are the audit representation of stock in transit.

## 3. Actor and access matrix

| Operation | Tenant | Staff |
|---|---|---|
| Create `PENDING` | Allowed with active contract for source/destination and active subscription | Allowed with the same conditions plus active assignment at source and destination |
| List/detail | Tenant-scoped active contract data | Tenant-scoped data only when the staff assignment rule is satisfied for the involved warehouse operation |
| Approve dispatch | Allowed; must be the Tenant, not Staff | Forbidden |
| Reject/cancel `PENDING` | Allowed | Forbidden |
| Receive `IN_TRANSIT` | Allowed; must be the Tenant | Forbidden |

The service layer must re-check Tenant scope, both current contracts, subscription, and the expected state. Controller permissions are not a replacement for these checks. A source or destination warehouse from another Tenant must never be observable through a transfer response.

## 4. Persistence decision

The transfer uses four new tables. Child tables use the existing `is_active`, `is_deleted`, `created_at`, and `updated_at` conventions where the entity maps them.

### `stock_transfers`

| Column | Rule |
|---|---|
| `id` | UUID primary key |
| `tenant_id` | Required Tenant owner |
| `source_warehouse_id` | Required; FK to source warehouse |
| `destination_warehouse_id` | Required; FK to destination warehouse |
| `status` | `PENDING`, `IN_TRANSIT`, `COMPLETED`, `REJECTED`, `CANCELLED` |
| `note` | Optional text |
| `created_by` | Required creator |
| `approved_by` | Nullable; set only on dispatch |
| `received_by` | Nullable; set only on receive |
| `rejected_by` | Nullable; set only on reject |
| `cancelled_by` | Nullable; set only on cancel |
| `decision_reason` | Nullable; required for reject and recommended for cancel |
| `approved_at` | Nullable dispatch time |
| `received_at` | Nullable receive time |
| `rejected_at` | Nullable reject time |
| `cancelled_at` | Nullable cancel time |
| `outbound_receipt_id` | Nullable unique FK to the generated outbound receipt |
| `inbound_receipt_id` | Nullable unique FK to the generated inbound receipt |

Database constraints must prevent a transfer from referencing the same source and destination warehouse. Indexes must support Tenant/status and source/destination warehouse filtering. The status constraint must use the exact values above.

### `stock_transfer_items`

| Column | Rule |
|---|---|
| `id` | UUID primary key |
| `transfer_id` | Required FK to transfer |
| `sku_id` | Required FK/reference to Product SKU |
| `requested_quantity` | Required integer greater than zero |

Add a unique constraint on `(transfer_id, sku_id)`. The item quantity is the amount that must be allocated from source and later allocated to destination.

### `stock_transfer_source_allocations`

| Column | Rule |
|---|---|
| `id` | UUID primary key |
| `item_id` | Required FK to transfer item |
| `source_stock_batch_id` | Required source batch reference |
| `source_rack_id` | Required location snapshot/reference |
| `source_bin_id` | Required location snapshot/reference |
| `quantity` | Required integer greater than zero |

Add a unique constraint on `(item_id, source_stock_batch_id)`. At create time the batch must match the item SKU and source warehouse, be active/not deleted, and be located at the submitted rack/bin. The sum of source allocation quantities must equal the item `requestedQuantity` before the transfer can be created.

### `stock_transfer_destination_allocations`

Destination allocations are intentionally stored in a separate table. Source and destination locations belong to different warehouses and have different validation timing; using one polymorphic allocation table would allow incomplete or ambiguous rows.

| Column | Rule |
|---|---|
| `id` | UUID primary key |
| `item_id` | Required FK to transfer item |
| `destination_rack_id` | Required destination rack reference |
| `destination_bin_id` | Required destination bin reference |
| `quantity` | Required integer greater than zero |

Add a unique constraint on `(item_id, destination_rack_id, destination_bin_id)`. Destination allocations are supplied only by the receive request. Their sum must equal the item `requestedQuantity`; the destination rack/bin must belong to the active Tenant layout of the destination warehouse at receive time.

No transfer fields are added to `StockBatch`. Current stock remains the source of truth for stock in warehouses; a transfer in `IN_TRANSIT` is represented by the transfer aggregate itself.

## 5. Receipt and transaction audit

Dispatch creates one internal `InventoryReceipt` of type `OUTBOUND` with status `APPROVED`, one receipt item per source allocation, and one negative `InventoryTransaction` per deducted source batch. Receive creates one internal `InventoryReceipt` of type `INBOUND` with status `APPROVED`, one receipt item per destination allocation, and one positive `InventoryTransaction` per destination stock batch.

The transfer stores the generated outbound and inbound receipt IDs. The receipts use their existing `referenceId` to point back to the transfer ID. FE does not need to call receipt APIs for this workflow. The internal receipt creation and stock mutation must be in the same database transaction as the transfer state change.

The existing receipt public workflow remains unchanged. The transfer service may extract a reusable internal stock-movement helper, but must not call the public receipt endpoint internally or duplicate capacity/stock arithmetic.

## 6. Idempotency and locking contract

Every dispatch and receive operation must:

1. Lock the transfer row with a pessimistic write lock.
2. Re-read the current transfer state inside the transaction.
3. Return `409 Conflict` when the expected state is no longer valid.
4. Lock all affected stock batches or destination rack/bin rows in deterministic UUID order.
5. Re-check quantity, ownership, warehouse, location, capacity, and allocation totals after locks are acquired.
6. Create the internal receipt, transactions, allocation records, actor/timestamp, and state change atomically.

The transfer receipt link is unique and the state check is mandatory. A retry after a successful request must not create another receipt or change stock again. If any validation fails, the whole transaction rolls back, leaving the transfer and stock unchanged.

## 7. API contract

All endpoints return the existing `ApiResponse` envelope. UUIDs below are examples only.

### Create transfer

```http
POST /api/tenant/inventory/transfers
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "sourceWarehouseId": "11111111-1111-1111-1111-111111111111",
  "destinationWarehouseId": "22222222-2222-2222-2222-222222222222",
  "note": "Move stock to the second warehouse",
  "items": [
    {
      "skuId": "33333333-3333-3333-3333-333333333333",
      "requestedQuantity": 10,
      "sourceAllocations": [
        {
          "sourceStockBatchId": "44444444-4444-4444-4444-444444444444",
          "sourceRackId": "55555555-5555-5555-5555-555555555555",
          "sourceBinId": "66666666-6666-6666-6666-666666666666",
          "quantity": 10
        }
      ]
    }
  ]
}
```

The response has `status: "PENDING"`, source/destination warehouse summaries, item/source allocations, empty destination allocations, creator, and timestamps. Stock is unchanged.

### List and detail

```http
GET /api/tenant/inventory/transfers?sourceWarehouseId=<uuid>&destinationWarehouseId=<uuid>&status=PENDING&page=0&size=10
GET /api/tenant/inventory/transfers/{transferId}
```

List uses the existing `PagedResponse` fields: `content`, `page`, `size`, `totalElements`, `totalPages`, and `last`. Detail and list entries expose the same state and warehouse/item summaries; detail includes both allocation lists and audit actors/timestamps.

### Approve dispatch

```http
PATCH /api/tenant/inventory/transfers/{transferId}/approve-dispatch
```

No request body is required. A successful response has `status: "IN_TRANSIT"`, `approvedBy`, `approvedAt`, and the outbound receipt reference. Source quantities have been deducted once; destination quantities remain unchanged.

### Reject or cancel a pending transfer

```http
PATCH /api/tenant/inventory/transfers/{transferId}/reject
Content-Type: application/json

{
  "reason": "Source allocation is not ready"
}
```

```http
PATCH /api/tenant/inventory/transfers/{transferId}/cancel
Content-Type: application/json

{
  "reason": "The tenant cancelled the planned move"
}
```

Both operations are valid only from `PENDING`, do not change stock, and return the terminal state with actor, timestamp, and reason.

### Receive transfer

```http
POST /api/tenant/inventory/transfers/{transferId}/receive
Content-Type: application/json
```

```json
{
  "destinationAllocations": [
    {
      "itemId": "77777777-7777-7777-7777-777777777777",
      "destinationRackId": "88888888-8888-8888-8888-888888888888",
      "destinationBinId": "99999999-9999-9999-9999-999999999999",
      "quantity": 10
    }
  ]
}
```

The response has `status: "COMPLETED"`, persisted destination allocations, `receivedBy`, `receivedAt`, and the inbound receipt reference. Destination stock is increased once and capacity is checked using the shared physical load calculator.

## 8. HTTP error contract

| Situation | HTTP status | Required behavior |
|---|---:|---|
| Invalid UUID/body/quantity/allocation total | `400` | Do not change stock or transfer state |
| Expected state does not match current state, duplicate/retry transition, or forbidden cancel after dispatch | `409` | Do not create another receipt or movement |
| Missing active contract, subscription, assignment, or role/permission | `403` | Do not reveal cross-tenant details |
| Transfer, SKU, batch, warehouse, rack, or bin is outside the tenant scope/not found | `404` | Return the existing API error envelope |
| Unexpected persistence/provider error | `5xx` | Transaction must roll back; FE may retry only after reloading detail |

Error responses use `ApiResponse` with `success: false`, stable `code` when available, and `message`.

## 9. Conservation rules for tests

For each SKU and physical movement:

- `PENDING`: `source stock + destination stock` is unchanged; no receipt/transaction is generated.
- `IN_TRANSIT`: source is reduced by the requested quantity; destination is unchanged; the transfer quantity is the in-transit amount.
- `COMPLETED`: destination increases by exactly the requested quantity; total source plus destination equals the pre-dispatch total.
- `REJECTED` or `CANCELLED` from `PENDING`: no stock or receipt movement.
- A second dispatch/receive request, concurrent or retried, must not change any quantity twice.
