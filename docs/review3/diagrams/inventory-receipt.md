# Inventory Receipt UML Sources

## Inventory Receipt Class Diagram

```plantuml
@startuml
title Inventory Receipt Class Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam classAttributeIconSize 0
hide empty members
left to right direction

class InventoryReceiptController {
  +createReceipt()
  +approveReceipt()
  +rejectReceipt()
  +getReceipts()
  +getReceiptDetail()
}

class InventoryReceiptService {
  +createReceipt()
  +approveReceipt()
  +rejectReceipt()
  +getReceiptsByWarehouse()
}

interface InventoryReceiptRepository
interface StockBatchRepository
interface InventoryTransactionRepository

class InventoryReceipt
class InventoryReceiptItem
class StockBatch
class InventoryTransaction

InventoryReceiptController ..> InventoryReceiptService
InventoryReceiptService ..> InventoryReceiptRepository
InventoryReceiptService ..> StockBatchRepository
InventoryReceiptService ..> InventoryTransactionRepository
InventoryReceipt "1" *-- "1..*" InventoryReceiptItem
InventoryReceiptItem ..> StockBatch
InventoryReceipt "1" ..> InventoryTransaction
@enduml
```

## Create and Approve Inbound Receipt Sequence Diagram

```plantuml
@startuml
title Create and Approve Inbound Receipt Sequence Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
hide footbox
autonumber

actor Tenant
participant "InventoryReceiptController" as Controller
participant "InventoryReceiptService" as Service
database "InventoryReceiptRepository" as ReceiptRepo
database "StockBatchRepository" as StockRepo
database "InventoryTransactionRepository" as TransactionRepo

Tenant -> Controller: createReceipt(CreateInventoryReceiptRequest)
activate Controller
Controller -> Service: createReceipt(userId, request)
activate Service
Service -> ReceiptRepo: save PENDING inbound receipt
ReceiptRepo --> Service: receipt
Service --> Controller: InventoryReceiptResponse(PENDING)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller

Tenant -> Controller: approveReceipt(receiptId)
activate Controller
Controller -> Service: approveReceipt(approverId, receiptId)
activate Service
Service -> StockRepo: lock and update stock batches
StockRepo --> Service: updated stock
Service -> TransactionRepo: save inbound transaction
TransactionRepo --> Service: transaction
Service -> ReceiptRepo: mark receipt APPROVED
ReceiptRepo --> Service: approved receipt
Service --> Controller: InventoryReceiptResponse(APPROVED)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller
@enduml
```

The backend re-checks stock and capacity during approval. A client must not
assume that a previous layout or put-away preview is still valid.
