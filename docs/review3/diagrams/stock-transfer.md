# Stock Transfer UML Sources

## Stock Transfer Class Diagram

```plantuml
@startuml
title Stock Transfer Class Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam classAttributeIconSize 0
hide empty members
left to right direction

class StockTransferController {
  +createTransfer()
  +getTransfers()
  +getTransfer()
  +approveDispatch()
  +receiveTransfer()
  +rejectTransfer()
  +cancelTransfer()
}

class StockTransferService {
  +createTransfer()
  +getTransfers()
  +getTransfer()
  +approveDispatch()
  +receiveTransfer()
  +rejectTransfer()
  +cancelTransfer()
}

interface StockTransferRepository
class StockTransfer
class StockTransferItem
class StockTransferSourceAllocation
class StockTransferDestinationAllocation
enum StockTransferStatus {
  PENDING
  IN_TRANSIT
  COMPLETED
  REJECTED
  CANCELLED
}

StockTransferController ..> StockTransferService
StockTransferService ..> StockTransferRepository
StockTransfer "1" *-- "1..*" StockTransferItem
StockTransferItem "1" *-- "0..*" StockTransferSourceAllocation
StockTransferItem "1" *-- "0..*" StockTransferDestinationAllocation
StockTransfer --> StockTransferStatus
@enduml
```

## Create, Dispatch and Receive Transfer Sequence Diagram

```plantuml
@startuml
title Create, Dispatch and Receive Stock Transfer Sequence Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
hide footbox
autonumber

actor Tenant
participant "StockTransferController" as Controller
participant "StockTransferService" as Service
database "StockTransferRepository" as TransferRepo
database "StockBatchRepository" as StockRepo

Tenant -> Controller: createTransfer(CreateStockTransferRequest)
activate Controller
Controller -> Service: createTransfer(userId, request)
activate Service
Service -> StockRepo: validate source batches and allocations
StockRepo --> Service: source stock is available
Service -> TransferRepo: save transfer PENDING
TransferRepo --> Service: transfer
Service --> Controller: StockTransferResponse(PENDING)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller

Tenant -> Controller: approveDispatch(transferId)
activate Controller
Controller -> Service: approveDispatch(userId, transferId)
activate Service
Service -> StockRepo: deduct source stock atomically
StockRepo --> Service: source stock updated
Service -> TransferRepo: mark transfer IN_TRANSIT
TransferRepo --> Service: transfer
Service --> Controller: StockTransferResponse(IN_TRANSIT)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller

Tenant -> Controller: receiveTransfer(transferId, allocations)
Controller -> Service: receiveTransfer(userId, transferId, request)
activate Service
Service -> StockRepo: add destination stock atomically
StockRepo --> Service: destination stock updated
Service -> TransferRepo: mark transfer COMPLETED
TransferRepo --> Service: transfer
Service --> Controller: StockTransferResponse(COMPLETED)
deactivate Service
Controller --> Tenant: success(data)
deactivate Controller
@enduml
```

## Stock Transfer State Machine Diagram

```plantuml
@startuml
title Stock Transfer State Machine Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam state {
  BackgroundColor white
  BorderColor black
  FontColor black
}

[*] --> PENDING : create transfer
PENDING --> IN_TRANSIT : approve dispatch
IN_TRANSIT --> COMPLETED : receive at destination
PENDING --> REJECTED : reject request
PENDING --> CANCELLED : cancel request
COMPLETED --> [*]
REJECTED --> [*]
CANCELLED --> [*]
@enduml
```

The diagram intentionally has no `APPROVED` state. Dispatch changes the state
to `IN_TRANSIT` after source stock is deducted; receiving adds stock at the
destination using destination-specific allocations.
