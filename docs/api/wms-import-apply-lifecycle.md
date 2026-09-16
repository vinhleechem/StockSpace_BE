# WMS Import Apply Lifecycle

## Scope

The import workflow is shared by three data groups:

- SKU Catalog
- Offline Inbound/Outbound Movements
- Inventory Audit Reconciliation

Every workflow has two phases:

1. Validate the workbook.
2. Apply the validated job.

Validation does not create or change catalog, stock, receipt, or audit data.
Apply is atomic for the selected workflow: if the domain operation fails, its
database changes are rolled back.

## Endpoints

### SKU Catalog

```text
POST /api/tenant/wms-data/catalog/imports/validate
POST /api/tenant/wms-data/catalog/imports/{jobId}/apply
```

### Offline Inbound/Outbound Movements

```text
POST /api/tenant/wms-data/warehouses/{warehouseId}/offline-movements/imports/validate
POST /api/tenant/wms-data/warehouses/offline-movements/imports/{jobId}/apply
```

### Inventory Audit Reconciliation

```text
POST /api/tenant/wms-data/inventory-audits/{auditId}/count-imports/validate
POST /api/tenant/wms-data/inventory-audits/count-imports/{jobId}/apply
```

All validate endpoints receive the workbook as multipart field `file`.

## SKU catalog apply contract

The catalog workbook contains `_META`, `CATEGORIES`, `SKUS`, and the
read-only `UOM_LOOKUP` sheet from the catalog export. `UOM_LOOKUP` is provided
for reference only; it is never imported.

The Apply service keeps the sheet boundaries explicit:

- Only rows from `CATEGORIES` may create tenant categories.
- Only rows from `SKUS` may create or update tenant SKUs.
- Rows with validation errors and rows whose action is `SKIP` are ignored by
  Apply.
- A category row is never interpreted as a SKU row, and a SKU row is never
  interpreted as a category row.

For every actionable `SKUS` row, `uom_code` is resolved case-insensitively to
an active, non-deleted unit of measure visible to the tenant. A tenant-owned
UOM has priority over a system UOM with the same code; the system UOM is used
only when the tenant has no active UOM with that code. The UOM is resolved for
all actionable SKU rows before any category or SKU write starts. Therefore, a
missing or no-longer-visible UOM fails the job without partially creating the
catalog.

The normal validation response reports an unavailable UOM as
`UOM_NOT_VISIBLE`. Apply repeats the lookup as a defense-in-depth check; a
stale or invalid job is recorded as `FAILED` with `UOM_NOT_FOUND` context.
After a job is `FAILED`, correct the workbook and validate a new job. Do not
retry the failed job.

The job can be reloaded with:

```text
GET /api/tenant/wms-data/imports/{jobId}
```

The response contains the authoritative job status and failure message. The
catalog export remains the source of truth for the next editable workbook;
existing system rows and the `UOM_LOOKUP` sheet remain read-only.

## Job status contract

| Status | Meaning | FE action |
|---|---|---|
| `VALIDATED` | Workbook passed validation and can be applied | Enable Apply |
| `INVALID` | Workbook contains validation errors | Show errors and require a new validation |
| `APPLIED` | Apply completed successfully | Show success and refresh data |
| `FAILED` | Apply domain transaction failed and failure was recorded | Show failure and require a new validation |

The Apply response is successful only when the returned job status is
`APPLIED`. FE must not show success merely because the HTTP request returned.

## Concurrent Apply

Only one Apply request may process a job at a time. A second request for the
same locked job receives:

```text
HTTP 409 Conflict
code: WMS_IMPORT_APPLY_IN_PROGRESS
```

The FE must disable the Apply button while a request is in progress and must
not send duplicate requests from multiple handlers or tabs.

If the first request has already completed, a later Apply receives a conflict
because the job is no longer `VALIDATED`. FE should reload the job status
instead of retrying blindly.

## Failure and retry rules

- Do not retry an `INVALID` job; correct the workbook and validate it as a new job.
- Do not retry an `APPLIED` job.
- A domain failure rolls back the business changes; it cannot leave a partial
  catalog, receipt, stock, or audit update committed by the Apply transaction.
- If the server returns a network error or 5xx, reload the job status before
  deciding whether to upload again. The original failure is preserved in the
  backend log even if recording the job failure encounters a second problem.

## FE integration checklist

- Keep the returned `jobId` from Validate.
- Call Apply exactly once for that job after the user confirms.
- Disable Apply during the request.
- On `200`, inspect `data.status` and require `APPLIED`.
- On `409 WMS_IMPORT_APPLY_IN_PROGRESS`, reload the job and show a retry-later
  message.
- On `FAILED`, show the failure state and ask the user to validate a new file.
- Refresh catalog, inventory, receipt, or audit data after successful Apply.
