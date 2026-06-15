# Danh Sách Công Việc Sprint 2 — Warehouse · Booking · Wallet · Inspection · Admin · Package

Tài liệu theo dõi toàn bộ công việc của **2 developer** cho sprint này.
Các module Auth + Admin User Management đã hoàn thành ở sprint trước.

> ✅ = Hoàn thành | 🔄 = Đang làm | ⬜ = Chưa làm | 🔗 = Phụ thuộc module khác

---

## 🗺️ Tổng Quan Luồng Nghiệp Vụ

```
[OWNER đăng kho] ──► [ADMIN duyệt / INSPECTOR kiểm định] ──► [Kho AVAILABLE]
                                                                      │
                                                              [TENANT tìm & đặt thuê]
                                                                      │
                                                              [OWNER approve booking]
                                                                      │
                                               ┌──────────────────────┘
                                               │
                                    [Deduct deposit từ Wallet TENANT]
                                               │
                                    [Tạo RentalContract]
                                               │
                              [Cả 2 bên confirm bàn giao → COMPLETED]
```

---

## ⚠️ Điểm Sync Giữa 2 Dev (Phải Làm Trước)

> Thực hiện ngay ngày 1 để 2 dev không block nhau.

- [x] **Dev A tạo skeleton `BookingRequest.java`** (chỉ cần entity + UUID field) để Dev B có FK reference
- [ ] **Dev B expose `WalletService.deductBalance()` + `WalletService.refundBalance()`** là `@Transactional` internal methods trước khi Dev A wire deposit flow
- [x] **Cả 2 thêm ErrorCode mới vào `ErrorCode.java`** (xem danh sách cuối file) — làm 1 lần tránh conflict

---

## 🅰 DEV A — Warehouse · Booking · Contract · Inspection

### ═══ MODULE 1: Warehouse (Đăng bài kho) ═══

#### 1.1. Entities & Enums
- [x] **`WarehouseType.java`** — `@Entity @Table("warehouse_types")`, fields: `id (serial)`, `name`, `description`
- [x] **`Warehouse.java`** — `@Entity @Table("warehouses")`, extend `BaseEntity`, fields theo DB schema (owner_id FK→User, type_id FK→WarehouseType, name, address, capacity, pricePerMonth, isVerified, status, policyVersionId)
- [x] **`WarehouseImage.java`** — `@Entity @Table("warehouse_images")`, fields: `id (uuid)`, `warehouse_id FK`, `imageUrl`, `displayOrder`
- [x] **`WarehouseLayout.java`** — `@Entity @Table("warehouse_layouts")`, fields: `id (uuid)`, `warehouse_id FK`, `tenant_id FK`, `isDefault`
- [x] **`WarehouseZone.java`** — `@Entity @Table("warehouse_zones")`, fields: `id (uuid)`, `layout_id FK`, `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`
- [x] **`WarehouseRack.java`** — `@Entity @Table("warehouse_racks")`, fields: `id (uuid)`, `zone_id FK`, `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX/Y/width/height`
- [x] **`WarehouseStatus.java`** (Enum) — `AVAILABLE`, `RENTED`, `PENDING_VERIFICATION`, `INACTIVE`

#### 1.2. Repositories
- [x] **`WarehouseTypeRepository.java`** — `JpaRepository<WarehouseType, Integer>`
- [x] **`WarehouseRepository.java`** — custom `@Query` search: filter theo `status`, `minPrice`, `maxPrice`, `minCapacity`, `keyword` (name/address), phân trang
- [x] **`WarehouseImageRepository.java`** — `findAllByWarehouseId(UUID)`

#### 1.3. DTOs
- [x] **`CreateWarehouseRequest.java`** — `typeId`, `name`, `address`, `capacity`, `pricePerMonth`
- [x] **`UpdateWarehouseRequest.java`** — các field cho phép sửa
- [x] **`WarehouseResponse.java`** — summary (dùng cho danh sách): `id`, `name`, `address`, `capacity`, `pricePerMonth`, `status`, `isVerified`, `imageUrls[]`
- [x] **`WarehouseDetailResponse.java`** — full detail + zones + layout info
- [x] **`WarehouseSearchRequest.java`** — `keyword`, `status`, `minPrice`, `maxPrice`, `minCapacity`
- [x] **`PagedWarehouseResponse.java`** — wrapper phân trang (giống `PagedUserResponse`)
- [x] **`WarehouseTypeResponse.java`**

#### 1.4. Services
- [x] **`WarehouseService.java`** — methods:
  - `createWarehouse(Long ownerId, CreateWarehouseRequest)` → `WarehouseResponse`
  - `updateWarehouse(Long ownerId, UUID id, UpdateWarehouseRequest)` → `WarehouseResponse`
  - `deleteWarehouse(Long ownerId, UUID id)` — chỉ xóa được nếu status `AVAILABLE`
  - `updateStatus(Long ownerId, UUID id, WarehouseStatus)` → `WarehouseResponse`
  - `getMyWarehouses(Long ownerId, Pageable)` → `PagedWarehouseResponse`
  - `addImages(UUID warehouseId, List<String> urls)` → `List<String>`
  - `searchWarehouses(WarehouseSearchRequest, Pageable)` → `PagedWarehouseResponse` **(public)**
  - `getWarehouseDetail(UUID id)` → `WarehouseDetailResponse` **(public)**

#### 1.5. Controllers
- [x] **`OwnerWarehouseController.java`** — `@RequestMapping("/api/owner/warehouses")`, `@PreAuthorize("hasRole('OWNER')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/owner/warehouses` | Tạo warehouse mới |
  | `PUT` | `/api/owner/warehouses/{id}` | Chỉnh sửa thông tin |
  | `DELETE` | `/api/owner/warehouses/{id}` | Xoá warehouse |
  | `PATCH` | `/api/owner/warehouses/{id}/status` | Cập nhật trạng thái |
  | `GET` | `/api/owner/warehouses` | Danh sách kho của mình (phân trang) |
  | `POST` | `/api/owner/warehouses/{id}/images` | Upload ảnh (nhận list URL) |

- [x] **`PublicWarehouseController.java`** — `@RequestMapping("/api/warehouses")`, không cần auth

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/warehouses` | Search & filter kho |
  | `GET` | `/api/warehouses/{id}` | Xem chi tiết kho |

---

### ═══ MODULE 2: Booking Request (Thuê kho) ═══

#### 2.1. Entities & Enums
- [x] **`BookingRequest.java`** ⚠️ (Tạo skeleton ngay ngày 1) — `@Entity @Table("booking_requests")`, fields: `id (uuid)`, `tenant_id FK→User`, `warehouse_id FK→Warehouse`, `depositAmount`, `status (ApprovalStatus)`, `policyVersionId`, extend `BaseEntity`
- [x] **`ApprovalStatus.java`** (Enum) — `PENDING`, `APPROVED`, `REJECTED`

#### 2.2. Repositories
- [x] **`BookingRequestRepository.java`** — `findByTenantId(Long, Pageable)`, `findByWarehouseOwnerId(Long, Pageable)`, `findByIdAndTenantId(UUID, Long)`

#### 2.3. DTOs
- [x] **`CreateBookingRequest.java`** — `warehouseId (UUID)`, `depositAmount`
- [x] **`BookingResponse.java`** — `id`, `tenant info`, `warehouse info`, `status`, `depositAmount`, `createdAt`
- [x] **`PagedBookingResponse.java`**
- [x] **`RejectBookingRequest.java`** — `reason (String)`

#### 2.4. Services
- [x] **`BookingService.java`** — methods:
  - `sendBookingRequest(Long tenantId, CreateBookingRequest)` → `BookingResponse` — kiểm tra warehouse `AVAILABLE` + tenant chưa có booking pending cho kho này
  - `cancelBooking(Long tenantId, UUID bookingId)` — chỉ cancel được khi status `PENDING`
  - `getMyBookings(Long tenantId, Pageable)` → `PagedBookingResponse`
  - `getIncomingRequests(Long ownerId, Pageable)` → `PagedBookingResponse`
  - `approveBooking(Long ownerId, UUID bookingId)` → `BookingResponse` 🔗 gọi `WalletService.deductBalance()` + tạo `RentalContract`
  - `rejectBooking(Long ownerId, UUID bookingId, String reason)` → `BookingResponse`

#### 2.5. Controllers
- [x] **`TenantBookingController.java`** — `@RequestMapping("/api/tenant/bookings")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/tenant/bookings` | Gửi yêu cầu thuê |
  | `GET` | `/api/tenant/bookings` | Lịch sử booking (phân trang) |
  | `DELETE` | `/api/tenant/bookings/{id}` | Hủy booking (nếu còn PENDING) |

- [x] **`OwnerBookingController.java`** — `@RequestMapping("/api/owner/bookings")`, `@PreAuthorize("hasRole('OWNER')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/owner/bookings` | Xem các request đến |
  | `PATCH` | `/api/owner/bookings/{id}/approve` | Chấp nhận yêu cầu |
  | `PATCH` | `/api/owner/bookings/{id}/reject` | Từ chối yêu cầu |

---

### ═══ MODULE 3: Rental Contract & Dispute ═══

#### 3.1. Entities & Enums
- [x] **`RentalContract.java`** — `@Entity @Table("rental_contracts")`, fields: `id (uuid)`, `booking_id FK (unique)`, `tenantConfirmation`, `ownerConfirmation`, `paperContractImages (jsonb)`, `status (ContractStatus)`, `startDate`, `endDate`
- [x] **`ContractStatus.java`** (Enum) — `ACTIVE`, `PENDING_HANDOVER`, `COMPLETED`, `DISPUTED`
- [x] **`DisputeTicket.java`** — `@Entity @Table("dispute_tickets")`, fields: `id (uuid)`, `contract_id FK (unique)`, `raisedBy FK→User`, `handledBy FK→User (null)`, `reason`, `evidenceImages (jsonb)`, `status`

#### 3.2. Repositories
- [x] **`RentalContractRepository.java`** — `findByBookingId(UUID)`, `findByTenantId(Long)`, `findByOwnerId(Long)`
- [x] **`DisputeTicketRepository.java`** — `findByRaisedBy(Long)`, `findByContractId(UUID)`

#### 3.3. DTOs
- [x] **`RentalContractResponse.java`** — đầy đủ thông tin hợp đồng
- [x] **`ConfirmHandoverRequest.java`** — `confirm (boolean)`
- [x] **`CreateDisputeRequest.java`** — `contractId`, `reason`, `evidenceImages[]`
- [x] **`DisputeResponse.java`**

#### 3.4. Services
- [x] **`ContractService.java`** — methods:
  - `createContractFromBooking(UUID bookingId, LocalDate start, LocalDate end)` — **internal**, gọi từ `BookingService.approveBooking()`
  - `getContractById(UUID id, Long userId)` → `RentalContractResponse`
  - `confirmHandover(Long userId, UUID contractId)` — khi cả 2 confirm → status `COMPLETED`, warehouse `AVAILABLE`
  - `getMyContracts(Long userId, Pageable)` → `Page<RentalContractResponse>`
- [x] **`DisputeService.java`** — methods:
  - `raiseDispute(Long userId, UUID contractId, CreateDisputeRequest)` → `DisputeResponse` — đổi contract status → `DISPUTED`
  - `getMyDisputes(Long userId, Pageable)` → `Page<DisputeResponse>`

#### 3.5. Controllers
- [x] **`ContractController.java`** — `@RequestMapping("/api/contracts")`, `@PreAuthorize("hasAnyRole('OWNER','TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/contracts` | Danh sách hợp đồng của mình |
  | `GET` | `/api/contracts/{id}` | Chi tiết hợp đồng |
  | `PATCH` | `/api/contracts/{id}/confirm-handover` | Xác nhận bàn giao |

- [x] **`DisputeController.java`** — `@RequestMapping("/api/disputes")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/disputes` | Mở dispute |
  | `GET` | `/api/disputes/mine` | Danh sách dispute của mình |

---

### ═══ MODULE 4: Inspection (Kiểm định kho) ═══

#### 4.1. Entities & Enums
- [x] **`InspectionReport.java`** — `@Entity @Table("inspection_reports")`, fields: `id (uuid)`, `warehouse_id FK`, `inspector_id FK→User (null)`, `checklistData (jsonb)`, `status`, `notes`, `inspectedAt`
- [x] **`InspectionStatus.java`** (Enum) — `PENDING`, `IN_PROGRESS`, `PASSED`, `FAILED`

#### 4.2. Repositories
- [x] **`InspectionReportRepository.java`** — `findByWarehouseId(UUID)`, `findByInspectorId(Long, Pageable)`, `findByStatus(InspectionStatus, Pageable)`

#### 4.3. DTOs
- [x] **`RequestInspectionRequest.java`** — `warehouseId (UUID)`
- [x] **`InspectionReportResponse.java`** — đầy đủ thông tin báo cáo
- [x] **`SubmitInspectionRequest.java`** — `checklistData (Map<String,Object>)`, `notes`, `status (PASSED/FAILED)`

#### 4.4. Services
- [x] **`InspectionService.java`** — methods:
  - `requestInspection(Long ownerId, UUID warehouseId)` → `InspectionReportResponse`
  - `getMyInspections(Long ownerId, Pageable)` — lịch sử kiểm định của warehouse owner
  - `getAssignedInspections(Long inspectorId, Pageable)` → `Page<InspectionReportResponse>`
  - `submitReport(Long inspectorId, UUID inspectionId, SubmitInspectionRequest)` → `InspectionReportResponse` — nếu `PASSED`: set `Warehouse.isVerified = true`

#### 4.5. Controllers
- [x] **`OwnerInspectionController.java`** — `@RequestMapping("/api/owner/inspections")`, `@PreAuthorize("hasRole('OWNER')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/owner/inspections` | Gửi yêu cầu kiểm định |
  | `GET` | `/api/owner/inspections` | Xem lịch sử kiểm định |

- [x] **`InspectorController.java`** — `@RequestMapping("/api/inspector/inspections")`, `@PreAuthorize("hasRole('INSPECTOR')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/inspector/inspections` | Xem danh sách được phân công |
  | `POST` | `/api/inspector/inspections/{id}/report` | Nộp báo cáo kiểm định |

---

## 🅱 DEV B — Wallet · Transaction · Service Package · Admin mở rộng

### ═══ MODULE 5: Wallet & Transaction (Ví & Giao dịch) ═══

#### 5.1. Entities & Enums
- [ ] **`Wallet.java`** — `@Entity @Table("wallets")`, fields: `id (uuid)`, `user_id FK→User (unique)`, `balance (decimal 15,2)`, extend `BaseEntity`
- [ ] **`Transaction.java`** — `@Entity @Table("transactions")`, fields: `id (uuid)`, `wallet_id FK`, `subscription_id FK (null)`, `booking_id FK (null)` 🔗, `amount`, `transactionType`, `paymentMethod`, `createdAt`
- [ ] **`WithdrawRequest.java`** — `@Entity @Table("withdraw_requests")`, fields: `id (uuid)`, `user_id FK`, `transaction_id FK (unique, null)`, `amount`, `bankName`, `bankAccountNumber`, `bankAccountHolder`, `status (ApprovalStatus)`, extend `BaseEntity`
- [ ] **`TransactionType.java`** (Enum) — `TOP_UP`, `WITHDRAWAL`, `DEPOSIT_PAYMENT`, `DEPOSIT_REFUND`, `PACKAGE_PAYMENT`, `COMMISSION`
- [ ] **`PaymentMethod.java`** (Enum) — `BANK_TRANSFER`, `VNPAY`, `MOMO`, `WALLET`

#### 5.2. Repositories
- [ ] **`WalletRepository.java`** — `findByUserId(Long)`, `findByUserIdWithLock(Long)` (dùng `@Lock(PESSIMISTIC_WRITE)` để tránh race condition)
- [ ] **`TransactionRepository.java`** — `findByWalletId(UUID, Pageable)`, `findAll(Pageable)` for Admin
- [ ] **`WithdrawRequestRepository.java`** — `findByUserId(Long, Pageable)`, `findByStatus(ApprovalStatus, Pageable)`

#### 5.3. DTOs
- [ ] **`WalletResponse.java`** — `id`, `userId`, `balance`, `updatedAt`
- [ ] **`TopUpRequest.java`** — `amount (BigDecimal)`, `paymentMethod`
- [ ] **`TransactionResponse.java`** — `id`, `amount`, `type`, `method`, `referenceId`, `createdAt`
- [ ] **`PagedTransactionResponse.java`**
- [ ] **`WithdrawRequestDto.java`** — `amount`, `bankName`, `bankAccountNumber`, `bankAccountHolder`
- [ ] **`WithdrawResponse.java`**

#### 5.4. Services
- [ ] **`WalletService.java`** ⚠️ — expose internal methods sớm nhất:
  - `getOrCreateWallet(Long userId)` → `Wallet` — tự tạo ví nếu chưa có
  - `getWalletInfo(Long userId)` → `WalletResponse`
  - `topUp(Long userId, TopUpRequest)` → `TransactionResponse`
  - **`deductBalance(Long userId, BigDecimal amount, String description)`** ← Dev A cần gọi khi approve booking
  - **`refundBalance(Long userId, BigDecimal amount, String description)`** ← Dev A gọi khi reject/cancel
- [ ] **`TransactionService.java`** — methods:
  - `getMyTransactions(Long userId, Pageable)` → `PagedTransactionResponse`
  - `recordTransaction(UUID walletId, BigDecimal amount, TransactionType, PaymentMethod, UUID refId)` — **internal**
  - `getAllTransactions(Pageable)` → `PagedTransactionResponse` (for Admin)
- [ ] **`WithdrawService.java`** — methods:
  - `submitWithdrawRequest(Long userId, WithdrawRequestDto)` → `WithdrawResponse` — check đủ số dư
  - `getMyWithdrawRequests(Long userId, Pageable)` → `Page<WithdrawResponse>`

#### 5.5. Controllers
- [ ] **`WalletController.java`** — `@RequestMapping("/api/wallet")`, `@PreAuthorize("isAuthenticated()")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/wallet` | Xem số dư ví |
  | `POST` | `/api/wallet/top-up` | Nạp tiền vào ví |
  | `GET` | `/api/wallet/transactions` | Lịch sử giao dịch (phân trang) |
  | `POST` | `/api/wallet/withdraw` | Tạo yêu cầu rút tiền |
  | `GET` | `/api/wallet/withdrawals` | Lịch sử yêu cầu rút |

---

### ═══ MODULE 6: Service Package & Subscription ═══

#### 6.1. Entities & Enums
- [ ] **`ServicePackage.java`** — `@Entity @Table("service_packages")`, fields: `id (serial)`, `name`, `features (jsonb)`, `price`, `durationDays`, extend `BaseEntity`
- [ ] **`Subscription.java`** — `@Entity @Table("subscriptions")`, fields: `id (uuid)`, `tenant_id FK→User`, `package_id FK→ServicePackage`, `startDate`, `endDate`, `status`, extend `BaseEntity`
- [ ] **`SubscriptionStatus.java`** (Enum) — `ACTIVE`, `EXPIRED`, `CANCELLED`

#### 6.2. Repositories
- [ ] **`ServicePackageRepository.java`** — `JpaRepository<ServicePackage, Integer>`
- [ ] **`SubscriptionRepository.java`** — `findByTenantIdAndStatus(Long, SubscriptionStatus)`, `findActiveByTenantIdAndWarehouseId(Long, UUID)` (dùng để guard WMS)

#### 6.3. DTOs
- [ ] **`ServicePackageResponse.java`** — `id`, `name`, `features`, `price`, `durationDays`
- [ ] **`SubscriptionResponse.java`** — `id`, `package info`, `startDate`, `endDate`, `status`
- [ ] **`PurchasePackageRequest.java`** — `packageId (int)`
- [ ] **`CreatePackageRequest.java`** — for Admin
- [ ] **`UpdatePackageRequest.java`** — for Admin

#### 6.4. Services
- [ ] **`ServicePackageService.java`** — methods:
  - `getAllPackages()` → `List<ServicePackageResponse>` **(public)**
  - `getPackageById(Integer id)` → `ServicePackageResponse`
  - `createPackage(CreatePackageRequest)` → `ServicePackageResponse` (Admin)
  - `updatePackage(Integer id, UpdatePackageRequest)` → `ServicePackageResponse` (Admin)
  - `deletePackage(Integer id)` (Admin)
- [ ] **`SubscriptionService.java`** — methods:
  - `purchasePackage(Long tenantId, PurchasePackageRequest)` → `SubscriptionResponse` 🔗 gọi `WalletService.deductBalance()`
  - `getMyActiveSubscription(Long tenantId)` → `SubscriptionResponse`
  - **`hasActiveSubscription(Long tenantId)`** → `boolean` — guard cho WMS features (Sprint 3)
  - `getAllSubscriptions(Pageable)` → Paged (Admin)

#### 6.5. Controllers
- [ ] **`PublicPackageController.java`** — `@RequestMapping("/api/packages")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/packages` | Xem danh sách gói dịch vụ |
  | `GET` | `/api/packages/{id}` | Chi tiết gói |

- [ ] **`TenantSubscriptionController.java`** — `@RequestMapping("/api/tenant/subscriptions")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/tenant/subscriptions` | Mua gói dịch vụ |
  | `GET` | `/api/tenant/subscriptions/active` | Gói dịch vụ đang active |

---

### ═══ MODULE 7: Admin mở rộng ═══

#### 7.1. Services mới (thêm vào package `admin/service/`)
- [ ] **`AdminWarehouseService.java`** — `getAllWarehouses(filter, Pageable)`, `verifyWarehouse(UUID)`, `rejectWarehouseListing(UUID, String reason)`
- [ ] **`AdminTransactionService.java`** — `getAllTransactions(Pageable, filter)` — thống kê toàn hệ thống
- [ ] **`AdminWithdrawService.java`** — `getAllWithdrawRequests(ApprovalStatus, Pageable)`, `approveWithdraw(UUID)` → tạo Transaction, `rejectWithdraw(UUID, reason)`
- [ ] **`AdminInspectionService.java`** — `getAllInspections(Pageable, filter)`, `assignInspector(UUID inspectionId, Long inspectorId)`
- [ ] **`AdminPackageService.java`** — delegate sang `ServicePackageService` + `getAllSubscriptions(Pageable)`
- [ ] **`AdminDisputeService.java`** — `getAllDisputes(status, Pageable)`, `resolveDispute(UUID id, String adminNote)` → đổi status + cập nhật hợp đồng

#### 7.2. Controllers mới (thêm vào package `admin/controller/`)
- [ ] **`AdminWarehouseController.java`** — `@RequestMapping("/api/admin/warehouses")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/warehouses` | Danh sách tất cả kho (filter: status, isVerified) |
  | `PATCH` | `/api/admin/warehouses/{id}/verify` | Duyệt kho (isVerified = true, status = AVAILABLE) |
  | `PATCH` | `/api/admin/warehouses/{id}/reject` | Từ chối listing |

- [ ] **`AdminTransactionController.java`** — `@RequestMapping("/api/admin/transactions")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/transactions` | Toàn bộ giao dịch hệ thống (phân trang, filter theo type) |

- [ ] **`AdminWithdrawController.java`** — `@RequestMapping("/api/admin/withdrawals")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/withdrawals` | Danh sách yêu cầu rút tiền (filter status) |
  | `PATCH` | `/api/admin/withdrawals/{id}/approve` | Duyệt rút tiền → tạo Transaction WITHDRAWAL |
  | `PATCH` | `/api/admin/withdrawals/{id}/reject` | Từ chối rút tiền |

- [ ] **`AdminInspectionController.java`** — `@RequestMapping("/api/admin/inspections")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/inspections` | Xem tất cả inspection requests (filter status) |
  | `PATCH` | `/api/admin/inspections/{id}/assign` | Gán inspector cho yêu cầu kiểm định |

- [ ] **`AdminPackageController.java`** — `@RequestMapping("/api/admin/packages")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/admin/packages` | Tạo gói dịch vụ mới |
  | `PUT` | `/api/admin/packages/{id}` | Sửa gói |
  | `DELETE` | `/api/admin/packages/{id}` | Xóa gói |
  | `GET` | `/api/admin/subscriptions` | Xem tất cả subscriptions |

- [ ] **`AdminDisputeController.java`** — `@RequestMapping("/api/admin/disputes")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/disputes` | Xem tất cả dispute (filter: OPEN/RESOLVED) |
  | `PATCH` | `/api/admin/disputes/{id}/resolve` | Giải quyết dispute + ghi note |

---

## 🔧 DÙNG CHUNG — Cả 2 Dev cần làm

### ErrorCode mới (thêm vào `ErrorCode.java`)
- [x] **Warehouse:** `WAREHOUSE_NOT_FOUND`, `WAREHOUSE_NOT_OWNED`, `WAREHOUSE_NOT_AVAILABLE`, `WAREHOUSE_ALREADY_VERIFIED`
- [x] **Booking:** `BOOKING_NOT_FOUND`, `BOOKING_ALREADY_PROCESSED`, `BOOKING_DUPLICATE_PENDING`
- [x] **Contract:** `CONTRACT_NOT_FOUND`, `CONTRACT_ALREADY_CONFIRMED`
- [x] **Inspection:** `INSPECTION_NOT_FOUND`, `INSPECTION_ALREADY_SUBMITTED`
- [x] **Wallet:** `WALLET_NOT_FOUND`, `WALLET_INSUFFICIENT_BALANCE`
- [x] **Package:** `PACKAGE_NOT_FOUND`, `SUBSCRIPTION_ALREADY_ACTIVE`, `SUBSCRIPTION_NOT_FOUND`
- [x] **Withdraw:** `WITHDRAW_REQUEST_NOT_FOUND`, `WITHDRAW_ALREADY_PROCESSED`
- [x] **Dispute:** `DISPUTE_NOT_FOUND`, `DISPUTE_ALREADY_OPEN`

---

## 💡 Conventions Cần Follow

### Package Structure
```
{module}/
  controller/     — @RestController, @PreAuthorize, @Tag(Swagger)
  dto/            — Request/Response, @Builder hoặc record
  entity/         — @Entity, extend BaseEntity, @Slf4j không cần
  repository/     — JpaRepository, custom @Query nếu cần
  service/        — @Service, @Transactional, @Slf4j, @RequiredArgsConstructor
```

### Response Pattern
```java
// Thành công
return ResponseEntity.ok(ApiResponse.success("Message", data));
return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Created", data));

// Lỗi — throw exception, KHÔNG return error trong controller
throw new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND);
throw new BadRequestException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
throw new ForbiddenException(ErrorCode.WAREHOUSE_NOT_OWNED);
```

### Security Pattern
```java
@PreAuthorize("hasRole('OWNER')")           // Class level
@PreAuthorize("hasRole('TENANT')")
@PreAuthorize("hasRole('ADMIN')")
@PreAuthorize("hasRole('INSPECTOR')")
@PreAuthorize("hasAnyRole('OWNER','TENANT')")
```

### Lấy User hiện tại
```java
User user = SecurityUtil.getCurrentUser()
        .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
```

### @Transactional cho Wallet (quan trọng — tránh race condition)
```java
// WalletRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
Optional<Wallet> findByUserIdWithLock(@Param("userId") Long userId);

// WalletService — deductBalance / refundBalance phải dùng lock này
```

---

## 🚀 Thứ Tự Implement Đề Xuất

| Ngày | Dev A | Dev B |
|------|-------|-------|
| 1 | ⬜ Entity Warehouse + WarehouseType + skeleton BookingRequest | ⬜ Entity Wallet + Transaction + WalletService (deduct/refund) |
| 2 | ⬜ WarehouseService + OwnerWarehouseController | ⬜ WalletController + TransactionService |
| 3 | ⬜ PublicWarehouseController + Search filter | ⬜ WithdrawService + Entity ServicePackage + Subscription |
| 4 | ⬜ BookingService đầy đủ + wire deduct deposit (🔗 Dev B) | ⬜ ServicePackageService + SubscriptionService + Controllers |
| 5 | ⬜ TenantBookingController + OwnerBookingController | ⬜ AdminWarehouseService/Controller + AdminWithdrawController |
| 6 | ⬜ Entity RentalContract + DisputeTicket + ContractService | ⬜ AdminTransactionController + AdminPackageController |
| 7 | ⬜ DisputeService + ContractController + DisputeController | ⬜ AdminDisputeController + AdminInspectionController |
| 8 | ⬜ Entity InspectionReport + InspectionService + Controllers | ⬜ Integration test + fix bugs |
| 9+ | ⬜ Integration test + wire Inspection → Warehouse verify | ⬜ End-to-end test toàn bộ luồng |
