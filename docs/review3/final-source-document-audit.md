# Plan 09 Final Source-to-Document Audit

## 1. Release record

| Item | Value |
|---|---|
| Backend source baseline audited | `5e7bf68` (`dev`, after Plan 08) |
| Documentation branch | `docs/review3-remediation` |
| Documentation commits | `d1b67c1`, `e018937`, `46c2054`, `8ebe254`, `47e3ab1` |
| Frontend source inspected | `main` at `cda9324`; no frontend source modified |
| Audit date | 2026-08-27 |

The backend source baseline did not change during Plan 09. Plan 09 adds and
validates documentation, UML sources/assets and the demo script only.

## 2. API mapping audit

The endpoints documented in
[`review3-frontend-integration-guide.md`](../api/review3-frontend-integration-guide.md)
were checked against the current controller mappings:

| Area | Controller source | Result |
|---|---|---|
| Tenant layout | `TenantLayoutController` | PASS |
| Staff layout | `StaffWarehouseLayoutController` | PASS |
| Capacity | `WarehouseCapacityController` | PASS |
| Stock/receipt | `StockBatchController`, `InventoryReceiptController` | PASS |
| Put-away | `PutawaySuggestionController` | PASS |
| Transfer | `StockTransferController` | PASS |
| Public search | `PublicWarehouseController` | PASS |
| Tenant staff | `TenantStaffController` | PASS |
| Staff self/operations | `StaffSelfController` | PASS |
| Inventory audit | `InventoryAuditController` | PASS |

The guide uses current request/response field names from the DTOs, including
`unitWeightKg`, `unitVolumeM3`, `capacityStatus`, `unallocatedQuantity`,
`sourceAllocations`, `destinationAllocations`, `provinceCode`, `districtCode`,
`warehouseTypeId`, `minRentalPrice` and `maxRentalPrice`.

## 3. State and method audit

| Diagram/source | Code source | States/methods checked | Result |
|---|---|---|---|
| Warehouse layout/capacity | `TenantLayoutController`, `StaffWarehouseLayoutController`, `WarehouseCapacityController`, corresponding services | `getLayout`, `saveLayout`, `getStaffLayoutTree`, `getCapacity`, `saveLayoutBulk` | PASS |
| Inventory receipt | `InventoryReceiptController`, `InventoryReceiptService`, `DocumentType`, `ApprovalStatus` | `createReceipt`, `approveReceipt`, `rejectReceipt`, `PENDING`, `APPROVED` | PASS |
| Stock transfer | `StockTransferController`, `StockTransferService`, `StockTransferStatus` | `createTransfer`, `approveDispatch`, `receiveTransfer`, `rejectTransfer`, `cancelTransfer`; `PENDING`, `IN_TRANSIT`, `COMPLETED`, `REJECTED`, `CANCELLED` | PASS |
| Inventory audit | `InventoryAuditController`, `InventoryAuditService`, `AuditStatus` | `createAudit`, `submitAudit`, `approveAudit`, `rejectAudit`; `PENDING`, `SUBMITTED`, `APPROVED`, `REJECTED` | PASS |
| Staff operations | `StaffSelfController`, `TenantStaffController`, services | `getOperations`, `getMyWorkHistory`, assignment operations; projection types `RECEIPT`, `AUDIT`, `TRANSFER` | PASS |
| Capacity read model | `CapacityStatus`, capacity DTOs and calculator | `EMPTY`, `AVAILABLE`, `FULL`, `OVER_CAPACITY` | PASS |

State diagrams use action labels rather than method names. State boxes are
rounded and have no compartments. Class diagrams show only the relevant
controller/service/repository/entity set and use the real public method names
without expanding every parameter, which keeps the diagrams readable.

## 4. Legacy and retired-flow audit

The following searches were executed against `src/main/java` and `src/test`:

| Search | Result | Interpretation |
|---|---|---|
| `BookingService`, `createContractFromBooking`, `deposit_percentage` | No active matches | Booking/deposit application flow is retired. |
| `contract.booking`, `getBooking()` | No active matches | Current WMS/contract code does not resolve access through Booking. |
| `DisputeService`, `DisputeController`, `PENDING_CANCEL`, `PENDING_HANDOVER`, `DISPUTED` | No active matches | Retired dispute/cancel/handover flow is not documented as current behavior. |
| `RENTED`, `markAsRented` | No active matches | Current warehouse lifecycle is not described with the retired status. |
| `bookingId`, `DEPOSIT_*` transaction values | Intentional legacy matches | Kept for historical transaction compatibility; not used by the Review 3 main flow. |
| `minPrice`, `maxPrice` | Intentional compatibility matches | Public search accepts legacy aliases; new FE integration uses `minRentalPrice` and `maxRentalPrice`. |

The old rental-contract checklist contains historical planning text and is not
treated as current source behavior. The current source scan, controller mapping
and release tests are authoritative.

## 5. UML and asset audit

| Check | Result |
|---|---|
| Markdown PlantUML source files | 5 files, 12 balanced `@startuml`/`@enduml` blocks |
| Exported review assets | 12 SVG files |
| PlantUML renderer | `1.2026.7`, successful exit for every block |
| Local creator paths in SVG | None found |
| SVG validity | Every file contains a complete `<svg>...</svg>` document |
| Method scope | Compact relevant methods only; no speculative classes |
| State convention | Actual backend enum values; action labels on transitions |
| Sequence scope | Main flow plus necessary persistence/response messages; no unrelated branch explosion |

Asset sources and import instructions are in
[`diagram-assets/README.md`](diagram-assets/README.md). SVG was chosen for
draw.io/report import so labels remain sharp when scaled.

## 6. Demo audit

The ordered demo script is in
[`review3-demo-script.md`](../demo/review3-demo-script.md). It was checked for:

- login/setup before any authenticated WMS action;
- two warehouses belonging to the same tenant for transfer demonstration;
- layout/capacity before inbound and transfer;
- put-away suggestion before final receipt/receive allocation;
- transfer lifecycle `PENDING → IN_TRANSIT → COMPLETED`;
- audit lifecycle `PENDING → SUBMITTED → APPROVED`;
- Staff assignment scope and read-only operation projection;
- public search using current filter names;
- recovery instructions for stale capacity, invalid state and missing access;
- every team member having a direct demo segment;
- no claim of Booking, deposit, dispute, handover or Staff Task board.

The script is repeatable once the team fills the private fixture IDs and runs the
rehearsal. The actual UI rehearsal duration is intentionally left for the team
to record because it cannot be executed from this backend workspace.

## 7. Verification commands and results

Executed on the documentation branch:

```text
.\mvnw.cmd -q -DskipTests compile                 PASS
.\mvnw.cmd -q '-Dtest=WarehouseLayoutServiceTest,WarehouseCapacityServiceTest,PutawaySuggestionPlannerTest,PutawaySuggestionServiceTest,StockTransferServiceTest,StockTransferDecisionServiceTest,StockTransferDispatchServiceTest,StockTransferReceiveServiceTest,StaffOperationsServiceTest,TenantStaffAssignmentTest,InventoryAuditServiceTest,PublicWarehouseSearchTest' test  PASS
git diff --check                                      PASS
```

The selected test run covered layout, capacity, put-away, transfer, staff,
audit and public warehouse search regression classes. The test JVM emitted the
existing Mockito dynamic-agent warning; it did not fail the build.

## 8. Final status

- [x] D01 use-case and scope traceability.
- [x] D02 API and frontend integration guide.
- [x] D03 affected UML source refresh.
- [x] D04 SVG review assets and quality record.
- [x] D05 ordered multi-member demo script.
- [x] D06 source-to-document audit.

Remaining manual action is the team's UI rehearsal: fill fixture IDs, verify the
FE clients against the guide, run the flow on the actual review environment and
record the duration before submission.
