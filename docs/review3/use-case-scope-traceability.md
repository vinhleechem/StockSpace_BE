# Review 3 Use-Case Scope and Traceability

## 1. Baseline and purpose

This document records the Review 3 scope against the backend release candidate
after Plans 01–08. It is the traceability index for the documentation and demo
work in Plan 09.

| Item | Value |
|---|---|
| Backend release baseline | `5e7bf68` (`dev`, merged Plan 08) |
| Backend repository | `StockSpace_BE` |
| Frontend source inspected | `StockSpace` `main`, `cda9324` |
| Scope | Review 3 remediation features and the main demo flow |
| Source of truth | Controller mappings, DTOs, enums, services, tests and committed API documents |

The frontend was inspected for integration coverage only. This backend task does
not modify frontend source code.

## 2. Feedback-to-delivery mapping

| Review feedback / required capability | Plan and backend evidence | API or UI surface | Verification evidence | Review 3 decision |
|---|---|---|---|---|
| Show concrete 3D warehouse capacity and stored stock | Plan 02–03; tenant layout guards, tenant layout customization and capacity read model | `GET /api/tenant/warehouses/{warehouseId}/layout/capacity`; tenant layout APIs; frontend 3D/layout screen | `4210656`, `ec300dd`, `3c90b8f`, `4264fe2` and `WarehouseCapacityServiceTest` | In scope. Capacity is read-only metrics; layout mutation remains a separate flow. |
| Provide a practical storage-location recommendation | Plan 05; deterministic capacity-aware heuristic | `POST /api/tenant/inventory/putaway/suggestions`; inbound/transfer receiving UI | `2b7226e`, `fc7bb43`, `a816acd`, `PutawaySuggestionPlannerTest`, `PutawaySuggestionServiceTest` | In scope. It is a suggestion only, not automatic stock mutation or full 3D packing. |
| Transfer stock between warehouses of one tenant | Plan 04; source and destination allocations are independent | `/api/tenant/inventory/transfers` create, list, detail, dispatch, receive, reject and cancel | `0fd7235`, `StockTransfer*Test` classes and `docs/stock-transfer-domain-contract.md` | In scope. No bin-to-bin mapping and no cross-tenant transfer. |
| Give staff practical warehouse operations | Plan 07; active assignment and contract checks plus read-only operation projection | `/api/staff/operations`, `/api/staff/my-work-history`, assigned layout and existing Receipt/Audit/Transfer APIs | `ae212ee`, `54c3094`, `1947247`, `87f3994`, `StaffOperationsServiceTest`, `TenantStaffAssignmentTest` | In scope. No new `StaffTask`/ticket entity; module APIs remain the mutation owners. |
| Demonstrate inventory audit | Plan 08; audit lifecycle and idempotent stock reconciliation | `/api/tenant/inventory/audits` create, list, detail, submit, approve, reject | `5ab7079`, `429a901`, `2b9cf05`, `c755300`, `docs/api/inventory-audit-lifecycle.md` | In scope. Tenant approval is the stock-adjustment boundary. |
| Improve warehouse search filters and pricing visibility | Plan 06; structured location/type/capacity filters and price aliases | `GET /api/warehouses`; public search UI | `e8582f3`, `bdc0525`, `996063c`, `4457c6a`, `docs/api/warehouse-search-filters.md` | In scope. Search fields must use the current names; legacy price aliases remain compatibility-only. |
| Keep the demo grounded in actual operations | Plan 09 D05; ordered flow and repeatable fixture | Login/setup → active access → layout → SKU/inbound/capacity → transfer/put-away → audit/staff → search | D05 demo script and final dry-run record | In scope. Use happy path first and reserve recovery cases for questions. |
| Remove the old booking/deposit flow from the Review 3 story | Rental-contract refactor already merged before Plans 01–08 | No active booking controller/API remains in the backend release; old transaction `bookingId` is legacy nullable storage | Rental refactor history and current controller scan | Excluded from the Review 3 main flow. Do not present it as an implemented current use case. |

## 3. Current frontend integration status

The following is an integration status record, not a request to change the
frontend in this repository.

| Capability | Backend contract available | Frontend status observed at `cda9324` | Required integration note |
|---|---:|---|---|
| Tenant layout read/write | Yes | Existing layout API client and layout screen | Keep the selected `warehouseId`; respect tenant layout scope and geometry errors. |
| Capacity metrics | Yes | No dedicated capacity client observed; some pages calculate/display partial values locally | Prefer the capacity endpoint for current weight, volume, utilization, status and stored SKUs. Do not treat client calculations as authoritative. |
| Put-away suggestions | Yes | No dedicated client observed | Add a client for the suggestion endpoint, show `unallocatedQuantity`, then submit final allocations through the existing receipt/transfer mutation. |
| Stock transfer | Yes | No dedicated transfer client observed | Add the full transfer lifecycle client; do not emulate a transfer by creating unrelated outbound and inbound receipts. |
| Inventory audit | Yes | Audit client/page exists; list call currently does not pass the optional `warehouseId` filter | Add the selected warehouse filter when needed and keep the status lifecycle from the backend enum. |
| Staff operations | Yes | Staff service covers assignment operations; no client for `/api/staff/operations` observed | Use the read-only projection for the staff operation list and route to the owning module for details/actions. |
| Warehouse search | Yes | Existing client sends only a subset of the current filter names | Add `provinceCode`, `districtCode`, `warehouseTypeId`, `maxCapacity`, `minRentalPrice` and `maxRentalPrice` as needed; do not silently rename them to retired aliases. |

## 4. Scope boundaries used by the demo and diagrams

- `ACTIVE` contract/access and an active WMS subscription are prerequisites for
  tenant WMS mutations where enforced by the service layer.
- Capacity metrics are derived from current physical SKU metadata and stock;
  `null`/`0` capacity follows the backend's unlimited convention.
- Put-away returns deterministic recommendations. It does not reserve stock,
  create a receipt, or guarantee that a later mutation will still succeed.
- A transfer moves stock between two warehouses of the same tenant. Source and
  destination layouts are independent; receiving chooses destination locations.
- Audit approval is the point at which discrepancy adjustments affect stock.
- Staff access is assignment- and warehouse-scoped. A read-only operation row is
  not a new task workflow.
- Booking, deposit, dispute and retired handover behavior are not part of this
  Review 3 WMS demo scope. They must not be reintroduced in diagrams or slides
  unless a separate approved requirement restores them.

## 5. Deferred or explicitly not claimed

The release does not claim the following behavior:

- full 3D bin-packing, aisle routing, collision detection or optimization;
- automatic put-away execution without user confirmation;
- chemical/food compatibility rules without a persisted storage-class field;
- cross-tenant stock transfer;
- a staff task board, SLA/deadline system or separate ticket persistence;
- frontend completion of capacity, put-away, transfer and staff-operation screens
  before those clients are integrated and smoke-tested.

These boundaries prevent the report and demo from presenting unimplemented
behavior as delivered functionality.

## 6. D01 acceptance check

- [x] Feedback is mapped to the relevant plan, API/UI surface and test evidence.
- [x] Capacity, put-away, transfer and staff operations are explicitly included.
- [x] Booking/retired flow is excluded from the current Review 3 main flow.
- [x] Frontend gaps are recorded without changing frontend source code.
- [x] Release baseline is recorded for the final source-to-document audit.
