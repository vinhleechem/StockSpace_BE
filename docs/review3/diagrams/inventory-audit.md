# Inventory Audit UML Sources

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
  +getMyAudits()
  +getAuditDetail()
  +submitAudit()
  +approveAudit()
  +rejectAudit()
}

class InventoryAuditService {
  +createAudit()
  +submitAudit()
  +approveAudit()
  +rejectAudit()
  +getMyAudits()
  +getAuditDetail()
}

interface InventoryAuditRepository
interface InventoryAuditItemRepository
interface StockBatchRepository
class InventoryAudit
class InventoryAuditItem
enum AuditStatus {
  PENDING
  SUBMITTED
  APPROVED
  REJECTED
}

InventoryAuditController ..> InventoryAuditService
InventoryAuditService ..> InventoryAuditRepository
InventoryAuditService ..> InventoryAuditItemRepository
InventoryAuditService ..> StockBatchRepository
InventoryAudit "1" *-- "1..*" InventoryAuditItem
InventoryAudit --> AuditStatus
@enduml
```

## Submit and Approve Inventory Audit Sequence Diagram

```plantuml
@startuml
title Submit and Approve Inventory Audit Sequence Diagram
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

Tenant -> Controller: createAudit(CreateInventoryAuditRequest)
activate Controller
Controller -> Service: createAudit(userId, request)
activate Service
Service -> AuditRepo: save audit PENDING
AuditRepo --> Service: audit PENDING
Service -> StockRepo: load current warehouse batches
StockRepo --> Service: batch snapshot
Service -> ItemRepo: save audit items with expected quantities
ItemRepo --> Service: audit items
Service --> Controller: InventoryAuditResponse(PENDING)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller

Staff -> Controller: submitAudit(auditId, actual quantities)
Controller -> Service: submitAudit(userId, auditId, request)
activate Service
Service -> AuditRepo: save actual quantities and discrepancies
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

[*] --> PENDING : create audit
PENDING --> SUBMITTED : submit actual quantities
PENDING --> REJECTED : reject audit
SUBMITTED --> APPROVED : approve reconciliation
SUBMITTED --> REJECTED : reject audit
APPROVED --> [*]
REJECTED --> [*]
@enduml
```

The states are the actual `AuditStatus` enum. Approval is the stock-adjustment
boundary; the state diagram does not imply a separate task or ticket entity.
