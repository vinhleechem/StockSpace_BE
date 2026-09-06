# Inventory Audit UML Sources

These diagrams describe the single canonical audit workflow. The historical
snapshot/submit/reject flow is no longer exposed or writable.

## Inventory Audit Class Diagram

```plantuml
@startuml
title Inventory Audit Class Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam classAttributeIconSize 0
hide empty members
left to right direction

class InventoryAuditController {
  +createAudit()
  +startAudit()
  +saveAuditCounts()
  +addUnexpectedItem()
  +submitAudit()
  +requestRecount()
  +cancelAudit()
  +approveAudit()
  +getAudits()
  +getAuditDetail()
}

class InventoryAuditService {
  +createAudit()
  +startAudit()
  +saveAuditCounts()
  +addUnexpectedItem()
  +submitAudit()
  +requestRecount()
  +cancelAudit()
  +approveAudit()
  +getAudits()
  +getAuditDetail()
}

interface InventoryAuditRepository
interface InventoryAuditItemRepository
interface StockBatchRepository
class InventoryAudit
class InventoryAuditItem
enum AuditStatus {
  DRAFT
  IN_PROGRESS
  SUBMITTED
  RECOUNT_REQUIRED
  APPROVED
  CANCELLED
}

InventoryAuditController ..> InventoryAuditService
InventoryAuditService ..> InventoryAuditRepository
InventoryAuditService ..> InventoryAuditItemRepository
InventoryAuditService ..> StockBatchRepository
InventoryAudit "1" *-- "1..*" InventoryAuditItem
InventoryAudit --> AuditStatus
@enduml
```

## Count and Reconcile Inventory Audit Sequence Diagram

```plantuml
@startuml
title Count and Reconcile Inventory Audit Sequence Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
hide footbox
autonumber

actor Staff
actor Tenant
participant "InventoryAuditController" as Controller
participant "InventoryAuditService" as Service
database "InventoryAuditRepository" as AuditRepo
database "StockBatchRepository" as StockRepo
database "InventoryAuditItemRepository" as ItemRepo
participant "InventoryReceiptService" as ReceiptService

Tenant -> Controller: createAudit(CreateInventoryAuditPlanRequest)
activate Controller
Controller -> Service: createAudit(userId, request)
activate Service
Service -> AuditRepo: save audit DRAFT
AuditRepo --> Service: audit DRAFT
Service --> Controller: InventoryAuditResponse(DRAFT)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller

Staff -> Controller: startAudit(auditId)
Controller -> Service: startAudit(userId, auditId)
activate Service
Service -> StockRepo: snapshot scoped stock and acquire movement lock
StockRepo --> Service: current stock
Service -> ItemRepo: save count items with expected quantities
ItemRepo --> Service: audit items
Service -> AuditRepo: mark audit IN_PROGRESS
AuditRepo --> Service: audit IN_PROGRESS
Service --> Controller: InventoryAuditResponse(IN_PROGRESS)
deactivate Service
Controller --> Staff: success(data)

Staff -> Controller: saveAuditCounts(auditId, counts)
Controller -> Service: saveAuditCounts(userId, auditId, request)
activate Service
Service -> ItemRepo: save actual quantities and discrepancies
ItemRepo --> Service: count items
Service --> Controller: InventoryAuditResponse(IN_PROGRESS)
deactivate Service
Controller --> Staff: success(data)

Staff -> Controller: submitAudit(auditId)
Controller -> Service: submitAudit(userId, auditId)
activate Service
Service -> AuditRepo: mark audit SUBMITTED
AuditRepo --> Service: audit SUBMITTED
Service --> Controller: InventoryAuditResponse(SUBMITTED)
deactivate Service
Controller --> Staff: success(data)

Tenant -> Controller: approveAudit(auditId)
Controller -> Service: approveAudit(approverId, auditId)
activate Service
Service -> ReceiptService: create adjustment receipt for discrepancies
ReceiptService --> Service: adjustment applied
Service -> AuditRepo: mark audit APPROVED
AuditRepo --> Service: audit
Service --> Controller: InventoryAuditResponse(APPROVED)
deactivate Service
Controller --> Tenant: success(data)
@enduml
```

## Inventory Audit State Machine Diagram

```plantuml
@startuml
title Inventory Audit State Machine Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam state {
  BackgroundColor white
  BorderColor black
  FontColor black
}

[*] --> DRAFT : create plan
DRAFT --> IN_PROGRESS : start count
IN_PROGRESS --> SUBMITTED : submit complete counts
SUBMITTED --> APPROVED : approve reconciliation
SUBMITTED --> RECOUNT_REQUIRED : request recount
RECOUNT_REQUIRED --> IN_PROGRESS : start recount
DRAFT --> CANCELLED : cancel audit
IN_PROGRESS --> CANCELLED : cancel audit
SUBMITTED --> CANCELLED : cancel audit
APPROVED --> [*]
CANCELLED --> [*]
@enduml
```

The states are the actual canonical `AuditStatus` enum. Approval is the
stock-adjustment boundary; the state diagram does not imply a separate task or
ticket entity.
