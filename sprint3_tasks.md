# Danh Sách Công Việc Sprint 3 — Notification · Warehouse Bin · WMS Phase 2 · Polish & Integration

Tài liệu theo dõi toàn bộ công việc của **2 developer** cho sprint này.
Sprint 2 đã hoàn thành: Auth · Warehouse (CRUD, 2D layout, kiểm định) · Booking · Contract · Dispute · Wallet · Subscription · Admin mở rộng · System Policy · Scheduler 7 ngày.

> ✅ = Hoàn thành | 🔄 = Đang làm | ⬜ = Chưa làm | 🔗 = Phụ thuộc module khác

---

## 🗺️ Tổng Quan Luồng Còn Lại

```
[Notification Subsystem] ──► Push thông báo tại các điểm quan trọng trong luồng nghiệp vụ
        │
[Warehouse_Bin Entity]   ──► Hoàn thiện cây cấu trúc kho: Layout → Zone → Rack → Bin
        │
[WMS Phase 2 — Inventory Management]
        │
  ┌─────┴──────────────────────────────────────────────────────────────┐
  │                                                                    │
[ProductCategory + ProductSKU]                             [StockBatch + Inventory Receipt]
  │                                                                    │
[Stock Batch với vị trí Bin]                               [Adjustment Note + Inventory Transaction]
        │
[API Tra cứu & Báo cáo tồn kho]
```

---

## ⚠️ Điểm Sync Giữa 2 Dev (Phải Làm Trước)

> Thực hiện ngay ngày 1 để không block nhau.

- [x] **Dev A tạo skeleton `WarehouseBin.java`** (entity + UUID field) trước khi Dev B dùng FK `bin_id` trong `StockBatch`
- [x] **Dev A expose `NotificationService.push(userId, title, message, type)`** là internal method trước khi Dev B hook vào các luồng Wallet/Subscription
- [ ] **Cả 2 thêm ErrorCode mới vào `ErrorCode.java`** — xem danh sách mục cuối file

---

## 🅰 DEV A — Notification · Warehouse Bin · WMS Inbound/Outbound

### ═══ MODULE 1: Notification Subsystem ═══

#### 1.1. Entity & Enum
- [ ] **`Notification.java`** — `@Entity @Table("notifications")`, fields: `id (uuid)`, `user_id FK→User`, `title`, `message`, `type (varchar 50)`, `isRead (boolean, default false)`, `createdAt`

#### 1.2. Repository
- [ ] **`NotificationRepository.java`** — `findByUserIdOrderByCreatedAtDesc(UUID, Pageable)`, `countByUserIdAndIsReadFalse(UUID)`, `markAllAsRead(UUID)` (custom `@Modifying @Query`)

#### 1.3. DTOs
- [ ] **`NotificationResponse.java`** — `id`, `title`, `message`, `type`, `isRead`, `createdAt`
- [ ] **`PagedNotificationResponse.java`**

#### 1.4. Service
- [ ] **`NotificationService.java`** — methods:
  - **`push(UUID userId, String title, String message, String type)`** ← ⚠️ internal, Dev B cần hook vào đây
  - `getMyNotifications(UUID userId, Pageable)` → `PagedNotificationResponse`
  - `markAsRead(UUID userId, UUID notificationId)`
  - `markAllAsRead(UUID userId)`
  - `countUnread(UUID userId)` → `long`

#### 1.5. Controller
- [ ] **`NotificationController.java`** — `@RequestMapping("/api/notifications")`, `@PreAuthorize("isAuthenticated()")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/notifications` | Danh sách thông báo của tôi (phân trang) |
  | `GET` | `/api/notifications/unread-count` | Số thông báo chưa đọc |
  | `PATCH` | `/api/notifications/{id}/read` | Đánh dấu 1 thông báo đã đọc |
  | `PATCH` | `/api/notifications/read-all` | Đánh dấu tất cả đã đọc |

#### 1.6. Hook thông báo vào các luồng hiện có (Sửa service sẵn có)
- [ ] **`WarehouseService.verifyWarehouse()`** → push cho Owner: `"Bài đăng kho {name} đã được Admin duyệt!"`
- [ ] **`WarehouseService.rejectWarehouse()`** → push cho Owner: `"Bài đăng kho {name} bị từ chối duyệt"`
- [ ] **`InspectionService.assignInspector()`** → push cho Inspector: `"Bạn được phân công kiểm định kho {name}"`
- [ ] **`InspectionService.submitReport()`** → push cho Owner nếu PASSED/FAILED
- [ ] **`ContractExpiryScheduler.expireContracts()`** → push cho Tenant: cảnh báo khi hợp đồng bị hủy tự động

---

### ═══ MODULE 2: Warehouse Bin (Hoàn thiện cấu trúc 2D) ═══

#### 2.1. Entity
- [ ] **`WarehouseBin.java`** ⚠️ (Tạo skeleton ngay ngày 1) — `@Entity @Table("warehouse_bins")`, fields: `id (uuid)`, `rack_id FK→WarehouseRack`, `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`, extend `BaseEntity`

#### 2.2. Repository
- [ ] **`WarehouseBinRepository.java`** — `findAllByRackId(UUID)`, `findByCode(String)`

#### 2.3. DTOs
- [ ] **`WarehouseBinResponse.java`** — `id`, `rackId`, `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`
- [ ] **`CreateWarehouseBinRequest.java`** — `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`
- [ ] **`UpdateWarehouseBinRequest.java`**

#### 2.4. Service (thêm method vào `WarehouseService` hoặc tạo `WarehouseLayoutService`)
- [ ] `getBinsByRack(UUID rackId)` → `List<WarehouseBinResponse>` — Tenant/Owner xem các Bin trong kệ
- [ ] `createBin(UUID ownerId, UUID rackId, CreateWarehouseBinRequest)` → `WarehouseBinResponse`
- [ ] `updateBin(UUID ownerId, UUID binId, UpdateWarehouseBinRequest)` → `WarehouseBinResponse`
- [ ] `deleteBin(UUID ownerId, UUID binId)`

#### 2.5. Controller (gộp vào `OwnerWarehouseController` hoặc tạo `OwnerLayoutController`)
- [ ] **`OwnerLayoutController.java`** — `@RequestMapping("/api/owner/warehouses/{warehouseId}/layout")`, `@PreAuthorize("hasRole('OWNER')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/owner/warehouses/{id}/layout/zones` | Xem danh sách Zone |
  | `GET` | `/api/owner/warehouses/{id}/layout/zones/{zoneId}/racks` | Xem danh sách Rack trong Zone |
  | `GET` | `/api/owner/warehouses/{id}/layout/racks/{rackId}/bins` | Xem danh sách Bin trong Rack |
  | `POST` | `/api/owner/warehouses/{id}/layout/bins` | Thêm Bin vào Rack |
  | `PUT` | `/api/owner/warehouses/{id}/layout/bins/{binId}` | Cập nhật Bin |
  | `DELETE` | `/api/owner/warehouses/{id}/layout/bins/{binId}` | Xóa Bin |

- [ ] **`PublicWarehouseController`** — thêm endpoint lấy full sơ đồ 2D

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/warehouses/{id}/layout` | Lấy toàn bộ sơ đồ kho (Zone → Rack → Bin) cho FE vẽ 2D |

---

### ═══ MODULE 3: WMS — Sản phẩm & Danh mục ═══

#### 3.1. Entities
- [ ] **`ProductCategory.java`** — `@Entity @Table("product_categories")`, fields: `id (uuid)`, `tenant_id FK→User`, `name`, `defaultAttributes (jsonb)`, `createdAt`
- [ ] **`ProductSku.java`** — `@Entity @Table("product_skus")`, fields: `id (uuid)`, `tenant_id FK→User`, `category_id FK→ProductCategory (null)`, `skuCode (unique)`, `name`, `unit`, `specifications (jsonb)`, extend `BaseEntity`

#### 3.2. Repositories
- [ ] **`ProductCategoryRepository.java`** — `findAllByTenantId(UUID)`
- [ ] **`ProductSkuRepository.java`** — `findAllByTenantId(UUID, Pageable)`, `findBySkuCodeAndTenantId(String, UUID)`, `existsBySkuCodeAndTenantId(String, UUID)`

#### 3.3. DTOs
- [ ] **`ProductCategoryResponse.java`** — `id`, `name`, `defaultAttributes`
- [ ] **`CreateCategoryRequest.java`** — `name`, `defaultAttributes (Map<String,Object>)`
- [ ] **`ProductSkuResponse.java`** — `id`, `skuCode`, `name`, `unit`, `specifications`, `categoryName`
- [ ] **`CreateSkuRequest.java`** — `categoryId`, `skuCode`, `name`, `unit`, `specifications`
- [ ] **`UpdateSkuRequest.java`**
- [ ] **`PagedSkuResponse.java`**

#### 3.4. Services
- [ ] **`ProductCategoryService.java`** — methods:
  - `getMyCategories(UUID tenantId)` → `List<ProductCategoryResponse>`
  - `createCategory(UUID tenantId, CreateCategoryRequest)` → `ProductCategoryResponse`
  - `deleteCategory(UUID tenantId, UUID categoryId)`
- [ ] **`ProductSkuService.java`** — methods:
  - `getMySKUs(UUID tenantId, Pageable)` → `PagedSkuResponse`
  - `getSkuDetail(UUID tenantId, UUID skuId)` → `ProductSkuResponse`
  - `createSku(UUID tenantId, CreateSkuRequest)` → `ProductSkuResponse` — validate `skuCode` unique per tenant
  - `updateSku(UUID tenantId, UUID skuId, UpdateSkuRequest)` → `ProductSkuResponse`
  - `deleteSku(UUID tenantId, UUID skuId)` — validate không có StockBatch đang dùng SKU này

#### 3.5. Controllers
- [ ] **`TenantProductController.java`** — `@RequestMapping("/api/tenant/products")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/tenant/products/categories` | Danh sách danh mục sản phẩm |
  | `POST` | `/api/tenant/products/categories` | Tạo danh mục mới |
  | `DELETE` | `/api/tenant/products/categories/{id}` | Xóa danh mục |
  | `GET` | `/api/tenant/products/skus` | Danh sách SKU (phân trang) |
  | `GET` | `/api/tenant/products/skus/{id}` | Chi tiết SKU |
  | `POST` | `/api/tenant/products/skus` | Tạo SKU mới |
  | `PUT` | `/api/tenant/products/skus/{id}` | Cập nhật SKU |
  | `DELETE` | `/api/tenant/products/skus/{id}` | Xóa SKU |

---

### ═══ MODULE 4: WMS — Phiếu Nhập/Xuất Kho ═══

#### 4.1. Entities
- [ ] **`InventoryReceipt.java`** — `@Entity @Table("inventory_receipts")`, fields: `id (uuid)`, `warehouse_id FK→Warehouse`, `created_by FK→User`, `type (DocumentType: INBOUND/OUTBOUND)`, `signatureData (text)`, `status (ApprovalStatus)`, `createdAt`
- [ ] **`InventoryReceiptItem.java`** (nếu cần chi tiết từng dòng) — `id`, `receipt_id FK`, `sku_id FK`, `quantity`, `zone_id FK (null)`, `rack_id FK (null)`, `bin_id FK (null)`, `note`

#### 4.2. Repository
- [ ] **`InventoryReceiptRepository.java`** — `findByWarehouseIdAndType(UUID, DocumentType, Pageable)`, `findByCreatedBy(UUID, Pageable)`

#### 4.3. DTOs
- [ ] **`CreateInventoryReceiptRequest.java`** — `warehouseId`, `type`, `items: List<{skuId, quantity, zoneId, rackId, binId, note}>`
- [ ] **`InventoryReceiptResponse.java`** — đầy đủ thông tin phiếu + danh sách dòng hàng
- [ ] **`PagedReceiptResponse.java`**

#### 4.4. Services
- [ ] **`InventoryReceiptService.java`** — methods:
  - `createReceipt(UUID userId, CreateInventoryReceiptRequest)` → `InventoryReceiptResponse` 🔗 cập nhật `StockBatch`
  - `approveReceipt(UUID staffId, UUID receiptId)` → `InventoryReceiptResponse`
  - `getReceiptsByWarehouse(UUID warehouseId, DocumentType, Pageable)` → `PagedReceiptResponse`

#### 4.5. Controllers
- [ ] **`InventoryReceiptController.java`** — `@RequestMapping("/api/tenant/inventory/receipts")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/tenant/inventory/receipts` | Tạo phiếu nhập/xuất kho |
  | `GET` | `/api/tenant/inventory/receipts` | Danh sách phiếu (filter type, phân trang) |
  | `GET` | `/api/tenant/inventory/receipts/{id}` | Chi tiết phiếu |
  | `PATCH` | `/api/tenant/inventory/receipts/{id}/approve` | Xác nhận/duyệt phiếu |

---

## 🅱 DEV B — WMS Stock · Adjustment · Báo cáo tồn kho · Hook Notification

### ═══ MODULE 5: WMS — Lô Hàng Tồn Kho ═══

#### 5.1. Entity
- [ ] **`StockBatch.java`** — `@Entity @Table("stock_batches")`, fields: `id (uuid)`, `sku_id FK→ProductSku`, `warehouse_id FK→Warehouse`, `zone_id FK→WarehouseZone (null)`, `rack_id FK→WarehouseRack (null)`, `bin_id FK→WarehouseBin (null)` 🔗, `quantity (int, default 0)`, `arrivalDate`, extend `BaseEntity`

#### 5.2. Repository
- [ ] **`StockBatchRepository.java`** — `findBySkuIdAndWarehouseId(UUID, UUID)`, `findByWarehouseId(UUID, Pageable)`, `findByBinId(UUID)`, `sumQuantityBySkuId(UUID)` (native/jpql query)

#### 5.3. DTOs
- [ ] **`StockBatchResponse.java`** — `id`, `skuCode`, `skuName`, `warehouseName`, `zoneName`, `rackName`, `binName`, `quantity`, `arrivalDate`
- [ ] **`PagedStockBatchResponse.java`**
- [ ] **`StockSummaryResponse.java`** — `skuId`, `skuCode`, `skuName`, `totalQuantity`, `locations[]`

#### 5.4. Services
- [ ] **`StockBatchService.java`** — methods:
  - `getStockByWarehouse(UUID tenantId, UUID warehouseId, Pageable)` → `PagedStockBatchResponse`
  - `getStockSummaryBySku(UUID tenantId, UUID skuId)` → `StockSummaryResponse` — tổng hợp số lượng theo SKU
  - `adjustQuantity(UUID batchId, int delta)` — **internal**, gọi từ InventoryReceiptService và AdjustmentNoteService
  - `findOrCreateBatch(UUID skuId, UUID warehouseId, UUID zoneId, UUID rackId, UUID binId)` → `StockBatch` — **internal**

#### 5.5. Controllers
- [ ] **`StockBatchController.java`** — `@RequestMapping("/api/tenant/inventory/stock")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/tenant/inventory/stock` | Xem toàn bộ tồn kho trong kho mình đang thuê |
  | `GET` | `/api/tenant/inventory/stock/summary` | Tổng hợp tồn kho theo SKU |
  | `GET` | `/api/tenant/inventory/stock/sku/{skuId}` | Tồn kho chi tiết theo SKU + vị trí |

---

### ═══ MODULE 6: WMS — Phiếu Điều Chỉnh Kho ═══

#### 6.1. Entity
- [ ] **`AdjustmentNote.java`** — `@Entity @Table("adjustment_notes")`, fields: `id (uuid)`, `batch_id FK→StockBatch`, `requested_by FK→User`, `approved_by FK→User (null)`, `quantityChange (int)`, `reason (text)`, `status (ApprovalStatus)`, `createdAt`

#### 6.2. Repository
- [ ] **`AdjustmentNoteRepository.java`** — `findByRequestedBy(UUID, Pageable)`, `findByBatchId(UUID)`, `findByStatus(ApprovalStatus, Pageable)`

#### 6.3. DTOs
- [ ] **`CreateAdjustmentRequest.java`** — `batchId`, `quantityChange (int, có thể âm nếu xuất)`, `reason`
- [ ] **`AdjustmentNoteResponse.java`** — `id`, `batchInfo`, `quantityChange`, `reason`, `status`, `requestedBy`, `approvedBy`, `createdAt`

#### 6.4. Services
- [ ] **`AdjustmentNoteService.java`** — methods:
  - `requestAdjustment(UUID userId, CreateAdjustmentRequest)` → `AdjustmentNoteResponse`
  - `approveAdjustment(UUID approverId, UUID adjustmentId)` → `AdjustmentNoteResponse` 🔗 gọi `StockBatchService.adjustQuantity()`
  - `rejectAdjustment(UUID approverId, UUID adjustmentId, String reason)` → `AdjustmentNoteResponse`
  - `getMyAdjustments(UUID userId, Pageable)` → `Page<AdjustmentNoteResponse>`

#### 6.5. Controllers
- [ ] **`AdjustmentNoteController.java`** — `@RequestMapping("/api/tenant/inventory/adjustments")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/tenant/inventory/adjustments` | Tạo phiếu điều chỉnh số lượng |
  | `GET` | `/api/tenant/inventory/adjustments` | Danh sách phiếu điều chỉnh |
  | `PATCH` | `/api/tenant/inventory/adjustments/{id}/approve` | Duyệt điều chỉnh |
  | `PATCH` | `/api/tenant/inventory/adjustments/{id}/reject` | Từ chối điều chỉnh |

---

### ═══ MODULE 7: WMS — Giao dịch Tồn Kho (Audit Trail) ═══

#### 7.1. Entity
- [ ] **`InventoryTransaction.java`** — `@Entity @Table("inventory_transactions")`, fields: `id (uuid)`, `receipt_id FK→InventoryReceipt (null)`, `adjustment_id FK→AdjustmentNote (null)`, `batch_id FK→StockBatch`, `quantityChanged (int)`, `createdAt`

#### 7.2. Repository
- [ ] **`InventoryTransactionRepository.java`** — `findByBatchId(UUID, Pageable)`, `findByReceiptId(UUID)`

#### 7.3. Service (thêm method vào `InventoryReceiptService` hoặc tách riêng)
- [ ] `recordTransaction(UUID receiptId, UUID adjustmentId, UUID batchId, int qty)` — **internal**
- [ ] `getTransactionsByBatch(UUID batchId, Pageable)` → `Page<InventoryTransactionResponse>`

#### 7.4. Controller (gộp vào StockBatchController hoặc tách riêng)
- [ ] **endpoint** `GET /api/tenant/inventory/stock/{batchId}/transactions` — Xem lịch sử biến động của 1 lô hàng

---

### ═══ MODULE 8: Hook Thông Báo (Dev B — Wallet & Subscription flow) ═══

> Dev B thêm lệnh gọi `notificationService.push()` vào các điểm sau sau khi Dev A expose được `NotificationService`:

- [ ] **`BookingService.sendBookingRequest()`** → push cho Owner: `"Có yêu cầu thuê kho mới cho {warehouseName}"`
- [ ] **`BookingService.approveBooking()`** → push cho Tenant: `"Yêu cầu thuê kho {warehouseName} đã được chấp nhận!"`
- [ ] **`BookingService.rejectBooking()`** → push cho Tenant: `"Yêu cầu thuê kho {warehouseName} bị từ chối"`
- [ ] **`ContractService.submitContractDocuments()`** → push cho Tenant: `"Owner đã cập nhật hợp đồng. Bạn có 7 ngày để ký xác nhận."`
- [ ] **`DisputeService.raiseDispute()`** → push cho Admin/Inspector: `"Có tranh chấp mới cần xử lý"`
- [ ] **`WalletService.topUp()`** (sau khi webhook SePay xác nhận) → push cho User: `"Ví đã được nạp {amount} VNĐ thành công"`
- [ ] **`WithdrawService.approveWithdraw()`** → push cho User: `"Yêu cầu rút tiền {amount} VNĐ đã được duyệt"`

---

### ═══ MODULE 9: Admin mở rộng cho WMS ═══

#### 9.1. Services & Controllers mới (thêm vào `admin/`)
- [ ] **`AdminInventoryController.java`** — `@RequestMapping("/api/admin/inventory")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/inventory/receipts` | Xem tất cả phiếu nhập/xuất kho toàn hệ thống |
  | `GET` | `/api/admin/inventory/adjustments` | Xem tất cả phiếu điều chỉnh toàn hệ thống |
  | `GET` | `/api/admin/inventory/stock` | Tổng hợp tồn kho toàn hệ thống theo warehouse |

---

## 🔧 DÙNG CHUNG — Cả 2 Dev cần làm

### ErrorCode mới (thêm vào `ErrorCode.java`)
- [ ] **Notification:** `NOTIFICATION_NOT_FOUND`
- [ ] **Product:** `PRODUCT_CATEGORY_NOT_FOUND`, `SKU_NOT_FOUND`, `SKU_CODE_DUPLICATE`
- [ ] **Stock:** `STOCK_BATCH_NOT_FOUND`, `STOCK_INSUFFICIENT_QUANTITY`
- [ ] **Receipt:** `INVENTORY_RECEIPT_NOT_FOUND`, `INVENTORY_RECEIPT_ALREADY_APPROVED`
- [ ] **Adjustment:** `ADJUSTMENT_NOT_FOUND`, `ADJUSTMENT_ALREADY_PROCESSED`
- [ ] **Bin:** `WAREHOUSE_BIN_NOT_FOUND`

---

## 💡 Conventions Cần Follow

### Package Structure WMS
```
wms/
  product/
    controller/     — TenantProductController
    dto/
    entity/         — ProductCategory, ProductSku
    repository/
    service/        — ProductCategoryService, ProductSkuService
  stock/
    controller/     — StockBatchController, AdjustmentNoteController
    dto/
    entity/         — StockBatch, AdjustmentNote, InventoryTransaction
    repository/
    service/        — StockBatchService, AdjustmentNoteService
  receipt/
    controller/     — InventoryReceiptController
    dto/
    entity/         — InventoryReceipt, InventoryReceiptItem
    repository/
    service/        — InventoryReceiptService
notification/
  controller/       — NotificationController
  dto/
  entity/           — Notification
  repository/
  service/          — NotificationService
```

### Guard WMS bằng Subscription
```java
// Trước khi gọi bất kỳ WMS API nào, kiểm tra Tenant có gói active không
boolean hasAccess = subscriptionService.hasActiveSubscription(tenantId);
if (!hasAccess) throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
```

### DocumentType Enum
```java
public enum DocumentType {
    INBOUND,   // Nhập kho
    OUTBOUND   // Xuất kho
}
```

---

## 🚀 Thứ Tự Implement Đề Xuất

| Ngày | Dev A | Dev B |
|------|-------|-------|
| 1 | ⬜ `WarehouseBin` entity + repo (sync point) + `NotificationService` skeleton | ⬜ `StockBatch` entity + repo + service skeleton |
| 2 | ⬜ `NotificationController` + Hook thông báo vào Warehouse/Inspection flow | ⬜ `ProductCategory` + `ProductSku` entity + service + controller |
| 3 | ⬜ `OwnerLayoutController` (Zone/Rack/Bin CRUD) + endpoint sơ đồ 2D | ⬜ `AdjustmentNote` entity + service + controller |
| 4 | ⬜ `ProductCategoryService` + `ProductSkuService` + `TenantProductController` | ⬜ Hook Notification vào Booking/Wallet/Contract flow |
| 5 | ⬜ `InventoryReceipt` + `InventoryReceiptItem` entity + service | ⬜ `InventoryTransaction` entity + audit trail service |
| 6 | ⬜ `InventoryReceiptController` + wire update StockBatch | ⬜ `AdminInventoryController` |
| 7 | ⬜ Integration test: luồng nhập/xuất kho đầy đủ | ⬜ Integration test: tồn kho + thông báo end-to-end |
| 8+ | ⬜ End-to-end test toàn bộ WMS Phase 2 | ⬜ Fix bugs + Performance test |
