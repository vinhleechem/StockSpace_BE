# Staff Operations UML Sources

## Staff Management and Operations Class Diagram

```plantuml
@startuml
title Staff Management and Operations Class Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
skinparam classAttributeIconSize 0
hide empty members
left to right direction

class TenantStaffController {
  +inviteStaff()
  +listStaffs()
  +removeStaff()
  +assignWarehouse()
  +getStaffAssignments()
  +revokeAssignment()
}

class StaffSelfController {
  +getMyWorkHistory()
  +getOperations()
}

class TenantStaffService {
  +sendInvitation()
  +listStaffs()
  +removeStaff()
  +assignWarehouseToStaff()
  +revokeWarehouseAssignment()
  +getStaffAssignments()
  +getStaffWorkHistory()
}

class StaffOperationsService {
  +getOperations()
}

interface StaffWarehouseAssignmentRepository
interface TenantMemberRepository
class StaffWarehouseAssignment
class StaffOperationResponse

TenantStaffController ..> TenantStaffService
StaffSelfController ..> TenantStaffService
StaffSelfController ..> StaffOperationsService
TenantStaffService ..> StaffWarehouseAssignmentRepository
StaffOperationsService ..> TenantMemberRepository
StaffWarehouseAssignmentRepository ..> StaffWarehouseAssignment
StaffOperationsService ..> StaffOperationResponse
@enduml
```

## Staff Reads Assigned Operations Sequence Diagram

```plantuml
@startuml
title Staff Reads Assigned Warehouse Operations Sequence Diagram
skinparam monochrome true
skinparam shadowing false
skinparam backgroundColor transparent
hide footbox
autonumber

actor Staff
participant "StaffSelfController" as Controller
participant "StaffOperationsService" as Service
database "StaffWarehouseAssignmentRepository" as AssignmentRepo
database "Receipt/Audit/Transfer Repositories" as WmsRepos

Staff -> Controller: getOperations(warehouseId, type, status, page, size)
activate Controller
Controller -> Service: getOperations(staffId, tenantId, filters, pageable)
activate Service
Service -> AssignmentRepo: load active assigned warehouses
AssignmentRepo --> Service: accessible warehouse IDs
Service -> WmsRepos: load matching active WMS operations
WmsRepos --> Service: receipts, audits and transfers
Service --> Controller: PagedResponse<StaffOperationResponse>
deactivate Service
Controller --> Staff: success(data)
deactivate Controller
@enduml
```

This is a read-only projection. Staff actions continue through the owning
Receipt, Audit and Transfer APIs, and the backend re-checks the active
assignment and contract for every request.
