# Put-away Suggestion API

This document describes the read-only put-away recommendation flow for the
frontend. The endpoint suggests rack/bin locations for inbound receiving or
transfer receiving. It does not create a receipt, change stock, create a
transaction, or reserve capacity.

## Endpoint

```http
POST /api/tenant/inventory/putaway/suggestions
```

Authentication is required. The authenticated user must have
`INVENTORY_READ`. The backend additionally requires:

- an active Contract for the selected warehouse;
- an active Subscription for the WMS operation;
- an active warehouse assignment when the user is Staff.

The selected layout must be the active tenant layout for that warehouse. The
owner default layout is not used to calculate a tenant put-away suggestion.

## Request

```json
{
  "warehouseId": "warehouse-uuid",
  "items": [
    {
      "skuId": "sku-uuid",
      "quantity": 10
    }
  ],
  "context": "INBOUND"
}
```

`context` accepts `INBOUND` or `TRANSFER_RECEIVE`. It is returned for frontend
traceability and does not change the calculation.

Request rules:

- `warehouseId`, `skuId`, `quantity`, and `context` are required;
- `quantity` must be greater than zero;
- one SKU may appear only once in a request;
- the SKU must belong to the current tenant, be active, and not be deleted;
- the SKU must have positive `unitWeightKg` and `unitVolumeM3`.

## Response

The normal response uses the existing `ApiResponse` envelope. The relevant
data shape is:

```json
{
  "success": true,
  "message": "Put-away suggestions calculated successfully",
  "data": {
    "warehouseId": "warehouse-uuid",
    "layoutId": "tenant-layout-uuid",
    "context": "INBOUND",
    "items": [
      {
        "skuId": "sku-uuid",
        "skuCode": "SKU-001",
        "skuName": "Product 1",
        "requestedQuantity": 10,
        "allocations": [
          {
            "rackId": "rack-uuid",
            "binId": "bin-uuid",
            "quantity": 6,
            "score": 1000,
            "reasons": [
              "Bin already contains the same SKU",
              "Partial allocation because the remaining capacity is limited",
              "Smallest remaining capacity among suitable locations",
              "Lower position is preferred for a weighted SKU",
              "Stable rack and bin code tie-break"
            ],
            "capacity": {
              "rack": {
                "locationId": "rack-uuid",
                "name": "Rack A",
                "currentWeightKg": 20,
                "currentVolumeM3": 4.5,
                "maxWeightKg": 100,
                "maxVolumeM3": 30,
                "remainingWeightKg": 68,
                "remainingVolumeM3": 22.5
              },
              "bin": {
                "locationId": "bin-uuid",
                "name": "Bin A",
                "currentWeightKg": 8,
                "currentVolumeM3": 2,
                "maxWeightKg": 20,
                "maxVolumeM3": 5,
                "remainingWeightKg": 0,
                "remainingVolumeM3": 0
              }
            }
          }
        ],
        "unallocatedQuantity": 4,
        "warning": "Insufficient physical capacity for the requested quantity"
      }
    ]
  }
}
```

`allocations` can contain more than one bin. The frontend must use
`unallocatedQuantity` instead of assuming that the requested quantity was
fully placed. For every item:

```text
sum(allocation.quantity) + unallocatedQuantity = requestedQuantity
```

When all requested quantity is allocated, `unallocatedQuantity` is `0` and
`warning` is omitted. When there is no suitable capacity, `allocations` is
empty and the whole requested quantity is unallocated.

For a capacity configured as `null` or `0`, the current backend convention is
unlimited. Its `remainingWeightKg` or `remainingVolumeM3` is therefore
`null`. Positive values are limits and are checked independently for both the
rack and the bin.

## Recommendation rules

The calculation is deterministic and explainable. Candidate bins are filtered
to active, non-deleted bins in active, non-deleted racks of the tenant layout.
Current active stock batches are loaded once for the selected warehouse.

Candidates are ranked in this order:

1. a bin already containing the same SKU;
2. a location that can hold the remaining quantity before a partial location;
3. the smaller remaining-capacity ratio (best-fit);
4. the lower position for a weighted SKU;
5. rack code, bin code, rack ID, and bin ID as stable tie-breakers.

The maximum quantity for a location is calculated from both physical limits:

```text
floor((maxWeight - currentWeight) / unitWeightKg)
floor((maxVolume - currentVolume) / unitVolumeM3)
minimum of the applicable values
```

Rack and bin capacity are both applied. The result is only a suggestion; it is
not an AI optimizer, a reservation, or a 3D collision/route calculation.

## Frontend confirmation flow

1. Select the warehouse and add the inbound or transfer-receive SKU quantities.
2. Call the suggestion endpoint.
3. Show each suggested rack/bin, quantity, reasons, capacity snapshot, and any
   `unallocatedQuantity`.
4. Allow the user to accept the suggestion or edit rack/bin quantities.
5. Submit the normal existing mutation API with the final allocations.

For inbound receiving, submit the accepted allocations through:

```http
POST /api/tenant/inventory/receipts
```

with `type: "INBOUND"` and `items` containing `skuId`, `quantity`, `rackId`,
and `binId`.

For transfer receiving, submit the accepted allocations through:

```http
POST /api/tenant/inventory/transfers/{transferId}/receive
```

with `destinationAllocations` containing the transfer `itemId`,
`destinationRackId`, `destinationBinId`, and `quantity`.

Receipt creation and approval, or transfer receiving, re-check current stock
and capacity under their own transaction/locking rules. A suggestion can
become stale if another operation uses the capacity first. If the final API
rejects it, reload the current data, request a new suggestion, and ask the user
to confirm again. Do not silently retry the old allocation.

## Common failures

The existing error envelope is used. Typical cases are:

| Situation | HTTP result | Frontend handling |
|---|---:|---|
| Missing/invalid request field | `400` | Show validation message and fix input |
| SKU has missing physical metadata | `400` | Ask the user to complete unit weight and volume |
| Warehouse or tenant layout not found | `404` | Reload warehouse/layout selection |
| Contract is expired or user is not assigned | `403` | Refresh access and stop the operation |
| Subscription is inactive/expired | `403` with `SUBSCRIPTION_REQUIRED` | Ask the tenant to activate a WMS subscription |
| Final receipt/receive fails after a stale suggestion | `400` or `409` | Reload capacity and request a new suggestion |

Do not create a stock batch or receipt only because the suggestion endpoint
returned `200`. Persistence occurs only through the existing receiving APIs.
