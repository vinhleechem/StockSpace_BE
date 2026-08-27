# Warehouse Layout and Capacity UML Sources

These PlantUML sources are the compact Review 3 version. They show only the
classes and messages affected by Plans 02–03. All dimensions are real meters;
capacity values are read-only metrics from the current stock and layout.

## Warehouse Layout and Capacity Class Diagram

```plantuml
@startuml
title Warehouse Layout and Capacity Class Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam classAttributeIconSize 0
hide empty members
left to right direction

class TenantLayoutController {
  +getLayout()
  +saveLayout()
}

class StaffWarehouseLayoutController {
  +getLayout()
}

class WarehouseCapacityController {
  +getCapacity()
}

class WarehouseLayoutService {
  +getLayoutTree()
  +getStaffLayoutTree()
  +saveLayoutBulk()
}

class WarehouseCapacityService {
  +getCapacity()
}

class PhysicalLoadCalculator {
  +calculate()
  +summarizeBySku()
  +assertWithinCapacity()
}

interface WarehouseLayoutRepository
interface WarehouseRackRepository
interface WarehouseBinRepository
interface StockBatchRepository

class WarehouseLayout
class WarehouseRack
class WarehouseBin

TenantLayoutController ..> WarehouseLayoutService
StaffWarehouseLayoutController ..> WarehouseLayoutService
WarehouseCapacityController ..> WarehouseCapacityService
WarehouseLayoutService ..> WarehouseLayoutRepository
WarehouseLayoutService ..> WarehouseRackRepository
WarehouseLayoutService ..> WarehouseBinRepository
WarehouseCapacityService ..> WarehouseLayoutRepository
WarehouseCapacityService ..> StockBatchRepository
WarehouseCapacityService ..> PhysicalLoadCalculator
WarehouseLayout "1" *-- "0..*" WarehouseRack
WarehouseRack "1" *-- "0..*" WarehouseBin
@enduml
```

## Read Capacity Sequence Diagram

```plantuml
@startuml
title Read Warehouse Layout Capacity Sequence Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
hide footbox
autonumber

actor "Tenant / Staff" as User
participant "WarehouseCapacityController" as Controller
participant "WarehouseCapacityService" as Service
database "WarehouseLayoutRepository" as LayoutRepo
database "StockBatchRepository" as StockRepo
participant "PhysicalLoadCalculator" as Calculator

User -> Controller: getCapacity(warehouseId)
activate Controller
Controller -> Service: getCapacity(tenantId, warehouseId, staffId)
activate Service
Service -> LayoutRepo: load accessible active layout
LayoutRepo --> Service: layout with racks and bins
Service -> StockRepo: load active stock for warehouse
StockRepo --> Service: stock load lines
Service -> Calculator: calculate physical load and utilization
Calculator --> Service: rack/bin metrics and SKU summaries
Service --> Controller: WarehouseLayoutCapacityResponse
deactivate Service
Controller --> User: success(data)
deactivate Controller
@enduml
```

The capacity response is a read model. It does not reserve space or mutate
stock. Authorization and warehouse/contract/assignment checks remain in the
backend service layer.
