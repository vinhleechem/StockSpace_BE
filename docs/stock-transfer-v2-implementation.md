# Stock transfer V2 implementation

The transfer module now supports a safe execution path in addition to the
legacy all-in-one dispatch endpoint.

## Execution path

```text
PENDING --allocate--> ALLOCATED --pick--> PICKING/READY_TO_DISPATCH
                                      --approve-dispatch--> IN_TRANSIT
                                      --arrive--> ARRIVED_AT_DESTINATION
                                      --receive--> RECEIVING --receive--> PARTIALLY_RECEIVED/COMPLETED
                                      --close-short--> SHORT_RECEIVED --retry--> RETRY_REQUESTED --dispatch--> IN_TRANSIT
                                      --reject-receipt--> RECEIVE_REJECTED --retry--> RETRY_REQUESTED
                                      --request-return--> RETURN_REQUESTED --dispatch--> RETURN_IN_TRANSIT
                                      --receive-return--> RETURNED/PARTIALLY_RETURNED
                                      --damaged--> RECONCILING --reconcile--> COMPLETED/LOST/RETURN_REQUESTED
```

`PENDING` does not change on-hand stock. `allocate` creates active reservations
against source batches. `approve-dispatch` consumes reservations and creates the
outbound receipt and negative inventory transactions atomically. `pick` stores
immutable scan lines and does not deduct stock until dispatch.

`/arrive` is an explicit checkpoint. For backward compatibility, `/receive`
also records `ARRIVED_AT_DESTINATION` and `RECEIVING` automatically when the
caller skips that checkpoint.

Creation accepts an optional `sourceStaffId`. When supplied, the ID must belong
to an active tenant staff member with an active assignment to the source
warehouse. Only that staff member can execute allocation/picking for the
transfer; the tenant remains the approver for dispatch. The assigned source
staff may run `allocate` and `pick` using only the source-warehouse assignment.

Creation also accepts an optional `destinationStaffId`. The assigned staff must
have an active assignment at the current destination and may execute `arrive`
and `receive`; exception decisions remain tenant-only. Retry may supply a new
`destinationStaffId`, and every attempt preserves its receiver snapshot.

Existing clients may continue calling `PATCH /{id}/approve-dispatch` directly
from `PENDING`; this is kept as a compatibility path while clients migrate to
`allocate → pick → approve-dispatch`.

## New endpoints

- `PATCH /api/tenant/inventory/transfers/{id}/allocate`
- `PATCH /api/tenant/inventory/transfers/{id}/destination-staff`
- `POST /api/tenant/inventory/transfers/{id}/pick`
- `POST /api/tenant/inventory/transfers/{id}/reconcile`
- `GET /api/tenant/inventory/transfers/{id}/timeline`
- `PATCH /api/tenant/inventory/transfers/{id}/arrive`
- `PATCH /api/tenant/inventory/transfers/{id}/reject-receipt`
- `PATCH /api/tenant/inventory/transfers/{id}/close-short`
- `POST /api/tenant/inventory/transfers/{id}/retry`
- `PATCH /api/tenant/inventory/transfers/{id}/retry/dispatch`
- `POST /api/tenant/inventory/transfers/{id}/return/request`
- `PATCH /api/tenant/inventory/transfers/{id}/return/dispatch`
- `POST /api/tenant/inventory/transfers/{id}/return/receive`

`PATCH /approve-dispatch`, `POST /receive`, and `POST /reconcile` accept the optional
`Idempotency-Key` header. Reusing a key with a different payload returns a
conflict; reusing it with the same payload returns the existing transfer result.

Tenant staff dropdowns can use `GET /api/tenant/staffs?warehouseId={id}&active=true`
to load only active staff assigned to the selected warehouse.

## Partial receiving

Set `allowPartial: true` on the receive request. Each allocation may specify a
`disposition`: `GOOD`, `QUARANTINE`, `DAMAGED`, or `REJECTED`.

- `GOOD` creates destination stock and a positive inventory transaction.
- Other dispositions are recorded on the transfer and do not become sellable
  stock automatically.
- Receiving all physical quantity with a non-good disposition moves the transfer
  to `RECONCILING`; a tenant must call `/reconcile` with an explicit resolution.
- Reconciliation records the signed decision. `RETURN_TO_SOURCE` opens the
  explicit return-leg workflow; `DECLARE_LOST` closes as `LOST` and still
  requires the normal loss/audit record in the WMS ledger.

The default `allowPartial: false` preserves the original all-or-nothing contract.

## Operational exceptions

`expectedArrivalAt` is optional on creation and defaults to 48 hours. The SLA
scheduler marks a silent `IN_TRANSIT`/arrival/partial transfer as `OVERDUE`
without changing stock. The operator must then explicitly reject, retry, or
request a return. A destination rejection is a separate `RECEIVE_REJECTED`
state and creates no inbound stock.

Every retry or return is stored in `stock_transfer_attempts` with a sequence,
route, quantities, timestamps and reason. This allows A → B → C forwarding,
multiple receiving sessions, and a documented return to A. Return receiving
creates a normal inbound receipt and stock batch at A; it is not an audit-only
status change. The return workflow only restores quantities not already accepted
as `GOOD`; accepted stock remains at the destination. Retrying after a completed
return is intentionally blocked so the operator must create a new outbound
request with fresh source allocation.

## Database migration

Apply `ops/migrations/20260909_01_stock_transfer_hardening.sql` and
`ops/migrations/20260909_02_stock_transfer_operational_legs.sql`, followed by
`ops/migrations/20260910_01_stock_transfer_destination_staff.sql`, with the normal
migration runner. They are additive, backfill transfer numbers/counters and an
initial outbound attempt for existing rows, add non-negative stock protection,
and create reservation, pick-line, event, idempotency and operational-attempt
tables.
