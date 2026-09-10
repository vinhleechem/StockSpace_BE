# Inventory Audit

The inventory audit API uses one public workflow at
`/api/tenant/inventory/audits`. The workflow marker kept in the database is an
internal compatibility detail and is not part of the API contract.

## Lifecycle

```text
DRAFT -> IN_PROGRESS -> SUBMITTED -> APPROVED
             ^              |
             |              +-> EDIT_REQUESTED -> REOPENED --submit--> SUBMITTED
             |              |
             +------ RECOUNT_REQUIRED --start--> IN_PROGRESS

DRAFT / IN_PROGRESS / SUBMITTED / EDIT_REQUESTED / REOPENED / RECOUNT_REQUIRED -> CANCELLED
```

`start` snapshots the current stock and acquires a warehouse movement lock.
Inbound, outbound, transfer and audit adjustments are rejected while the lock
is active. This conservative first version prevents a count from becoming
stale; the lock is released by approve, recount or cancel.

## Endpoints

```text
POST /api/tenant/inventory/audits
GET  /api/tenant/inventory/audits
POST /api/tenant/inventory/audits/{id}/start
PUT  /api/tenant/inventory/audits/{id}/counts
POST /api/tenant/inventory/audits/{id}/unexpected-items
POST /api/tenant/inventory/audits/{id}/submit
PUT  /api/tenant/inventory/audits/{id}/notes
POST /api/tenant/inventory/audits/{id}/request-edit
POST /api/tenant/inventory/audits/{id}/approve-edit
POST /api/tenant/inventory/audits/{id}/recount
POST /api/tenant/inventory/audits/{id}/approve
POST /api/tenant/inventory/audits/{id}/cancel
GET  /api/tenant/inventory/audits/{id}
```

Create accepts `warehouseId`, `scopeType` (`WAREHOUSE`, `RACK`, `BIN`), the
corresponding rack/bin ID, an optional `assignedToId` and note. The current
implementation snapshots existing tenant batches in that scope and presents
one count line per SKU/location (not one line per lot). The response includes
`countRound`, assignment and count timestamps.

Count requests use the server-issued `itemId`, never a client-created batch
ID. Every current-round item must be counted before submit. The count is blind
while the audit is `IN_PROGRESS`; after submit the API reveals the expected
quantity and discrepancy to the counter.

After submit, the staff response includes the expected quantity and discrepancy,
and staff may update only notes through `/notes`. Quantity updates are rejected
until the assigned staff member requests an edit and a tenant approves it.
Approval changes the same audit to `REOPENED`; the staff can correct quantities
and submit again, which locks the audit again. No separate count version is
created.

`unexpected-items` adds a SKU/location that was physically found but absent
from the snapshot. Approve re-reads and locks every relevant batch before
reconciling. Shortages are allocated oldest-arrival-first (FIFO); surpluses are
put into a new batch at that location. If book stock changed since the
snapshot, the whole approval fails with `AUDIT_STOCK_CHANGED` and no partial
adjustment is committed. Each batch-level delta is linked to an adjustment
record and receipt for idempotent traceability.
