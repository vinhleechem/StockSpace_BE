# Danh Sách Công Việc Sprint 3 — Notification · Warehouse Bin · WMS Phase 2 · Polish & Integration

Tài liệu theo dõi toàn bộ công việc của **2 developer** cho sprint này.
Sprint 2 đã hoàn thành: Auth · Warehouse (CRUD, 2D layout, kiểm định) · Booking · Contract · Dispute · Wallet · Subscription · Admin mở rộng · System Policy · Scheduler 7 ngày.

> ✅ = Hoàn thành | 🔄 = Đang làm | ⬜ = Chưa làm | 🔗 = Phụ thuộc module khác

---

## 🆕 Thay Đổi Lớn Sau Review Mentor (Cập nhật 2026-07)

> [!IMPORTANT]
> Mentor đã yêu cầu 2 cải tiến thiết kế DB quan trọng. **Dev A đã hoàn thành refactor code tương ứng, Dev B cần nắm rõ trước khi bắt đầu code.**

### ① Unit of Measure (UOM)
- Bảng `unit_of_measures` đã được thêm vào DB.
- Entity `ProductSku` đã **KHÔNG CÒN** trường `unit (String)` — thay bằng FK `uom_id → UnitOfMeasure.id`.
- Entity mới: [`UnitOfMeasure.java`](src/main/java/fu/stockspace/stockspace_be/wms/product/entity/UnitOfMeasure.java) (`id`, `name`, `symbol`, `type`).
- Repository mới: [`UnitOfMeasureRepository.java`](src/main/java/fu/stockspace/stockspace_be/wms/product/repository/UnitOfMeasureRepository.java).
- Toàn bộ DTO (`CreateSkuRequest`, `UpdateSkuRequest`, `ProductSkuResponse`) và `ProductSkuService` đã được cập nhật.
- **Dev B cần biết**: Khi code `StockBatchResponse`, trường hiển thị đơn vị tính phải lấy từ `sku.getUom().getSymbol()` thay vì `sku.getUnit()`.

### ② Quy trình Kiểm Kê Kho thay thế Adjustment Note
- **Xóa hoàn toàn khái niệm `AdjustmentNote`** — Module 6 trong task cũ đã được **viết lại thành Module Kiểm kê (Inventory Audit)**.
- Thiết kế nghiệp vụ mới:
  1. Tenant/Staff tạo phiếu kiểm kê `InventoryAudit` (status `PENDING`).
  2. Điền số lượng thực tế vào từng `InventoryAuditItem`.
  3. Nộp kiểm kê (`submitAudit`) — status chuyển sang `SUBMITTED`.
  4. Cấp trên duyệt (`approveAudit`):
     - Nếu có chênh lệch, **tự động sinh phiếu nhập/xuất điều chỉnh** (`InventoryReceipt` với `referenceId = audit.id`) và tự động cập nhật `StockBatch.quantity`.
  5. Mọi thay đổi số lượng đều được ghi nhận vào `InventoryTransaction` với `receipt_id` bắt buộc.
- Entity `InventoryReceipt` đã được thêm trường `referenceId (UUID, nullable)` để liên kết với `InventoryAudit.id`.
- Entity `InventoryTransaction` đã **xóa cột `adjustment_id`** — `receipt_id` bắt buộc NOT NULL.

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
[ProductCategory + ProductSKU + UnitOfMeasure]             [StockBatch + Inventory Receipt]
  │                                                                    │
[Stock Batch với vị trí Bin]                       [InventoryAudit → Auto Receipt → Transaction]
        │
[API Tra cứu & Báo cáo tồn kho]
```

---

## ⚠️ Điểm Sync Giữa 2 Dev (Phải Làm Trước)

> Thực hiện ngay ngày 1 để không block nhau.

- [x] **Dev A tạo skeleton `WarehouseBin.java`** (entity + UUID field) trước khi Dev B dùng FK `bin_id` trong `StockBatch`
- [x] **Dev A expose `NotificationService.push(userId, title, message, type)`** là internal method trước khi Dev B hook vào các luồng Wallet/Subscription
- [x] **Cả 2 thêm ErrorCode mới vào `ErrorCode.java`** — xem danh sách mục cuối file
- [x] **Dev A refactor `ProductSku` sang dùng `UnitOfMeasure` (FK `uom_id`)** — Dev B cần dùng `sku.getUom().getSymbol()` khi build DTO
- [x] **Dev A thêm `referenceId` vào `InventoryReceipt`** — Dev B dùng khi tạo phiếu điều chỉnh auto từ kết quả kiểm kê

---

## 🅰 DEV A — Notification · Warehouse Bin · WMS Inbound/Outbound

### ═══ MODULE 1: Notification Subsystem ═══

#### 1.1. Entity & Enum
- [x] **`Notification.java`** — `@Entity @Table("notifications")`, fields: `id (uuid)`, `user_id FK→User`, `title`, `message`, `type (varchar 50)`, `isRead (boolean, default false)`, `createdAt`

#### 1.2. Repository
- [x] **`NotificationRepository.java`** — `findByUserIdOrderByCreatedAtDesc(UUID, Pageable)`, `countByUserIdAndIsReadFalse(UUID)`, `markAllAsRead(UUID)` (custom `@Modifying @Query`)

#### 1.3. DTOs
- [x] **`NotificationResponse.java`** — `id`, `title`, `message`, `type`, `isRead`, `createdAt`
- [x] **`PagedNotificationResponse.java`**

#### 1.4. Service
- [x] **`NotificationService.java`** — methods:
  - **`push(UUID userId, String title, String message, String type)`** ← ⚠️ internal, Dev B cần hook vào đây
  - `getMyNotifications(UUID userId, Pageable)` → `PagedNotificationResponse`
  - `markAsRead(UUID userId, UUID notificationId)`
  - `markAllAsRead(UUID userId)`
  - `countUnread(UUID userId)` → `long`

#### 1.5. Controller
- [x] **`NotificationController.java`** — `@RequestMapping("/api/notifications")`, `@PreAuthorize("isAuthenticated()")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/notifications` | Danh sách thông báo của tôi (phân trang) |
  | `GET` | `/api/notifications/unread-count` | Số thông báo chưa đọc |
  | `PATCH` | `/api/notifications/{id}/read` | Đánh dấu 1 thông báo đã đọc |
  | `PATCH` | `/api/notifications/read-all` | Đánh dấu tất cả đã đọc |

#### 1.6. Hook thông báo vào các luồng hiện có (Sửa service sẵn có)
- [x] **`WarehouseService.verifyWarehouse()`** → push cho Owner: `"Bài đăng kho {name} đã được Admin duyệt!"`
- [x] **`WarehouseService.rejectWarehouse()`** → push cho Owner: `"Bài đăng kho {name} bị từ chối duyệt"`
- [x] **`InspectionService.assignInspector()`** → push cho Inspector: `"Bạn được phân công kiểm định kho {name}"`
- [x] **`InspectionService.submitReport()`** → push cho Owner nếu PASSED/FAILED
- [x] **`ContractExpiryScheduler.expireContracts()`** → push cho Tenant: cảnh báo khi hợp đồng bị hủy tự động

---

### ═══ MODULE 2: Warehouse Bin (Hoàn thiện cấu trúc 2D) ═══

#### 2.1. Entity
- [x] **`WarehouseBin.java`** ⚠️ (Tạo skeleton ngay ngày 1) — `@Entity @Table("warehouse_bins")`, fields: `id (uuid)`, `rack_id FK→WarehouseRack`, `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`, extend `BaseEntity`

#### 2.2. Repository
- [x] **`WarehouseBinRepository.java`** — `findAllByRackId(UUID)`, `findByCode(String)`

#### 2.3. DTOs
- [x] **`WarehouseBinResponse.java`** — `id`, `rackId`, `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`
- [x] **`CreateWarehouseBinRequest.java`** — `name`, `code`, `maxWeight`, `maxVolume`, `coordinateX`, `coordinateY`, `width`, `height`
- [x] **`UpdateWarehouseBinRequest.java`**

#### 2.4. Service (thêm method vào `WarehouseService` hoặc tạo `WarehouseLayoutService`)
- [x] `getBinsByRack(UUID rackId)` → `List<WarehouseBinResponse>` — Tenant/Owner xem các Bin trong kệ (đã tích hợp vào cây sơ đồ `getLayoutTree`)
- [x] `createBin(UUID ownerId, UUID rackId, CreateWarehouseBinRequest)` → `WarehouseBinResponse` (đã tối ưu bằng giải pháp Bulk Smart Sync lưu hàng loạt sơ đồ)
- [x] `updateBin(UUID ownerId, UUID binId, UpdateWarehouseBinRequest)` → `WarehouseBinResponse` (đã tối ưu bằng giải pháp Bulk Smart Sync)
- [x] `deleteBin(UUID ownerId, UUID binId)` (đã tối ưu bằng giải pháp Bulk Smart Sync kèm kiểm tra hàng tồn kho)

#### 2.5. Controller (gộp vào `OwnerWarehouseController` hoặc tạo `OwnerLayoutController`)
- [x] **`OwnerLayoutController.java`** — `@RequestMapping("/api/owner/warehouses/{warehouseId}/layout")`, `@PreAuthorize("hasRole('OWNER')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/owner/warehouses/{id}/layout` | Lấy sơ đồ layout mặc định (Owner) |
  | `PUT` | `/api/owner/warehouses/{id}/layout` | Lưu/cập nhật hàng loạt sơ đồ layout mặc định (Owner) |

- [x] **`PublicWarehouseController`** — thêm endpoint lấy full sơ đồ 2D

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/warehouses/{id}/layout` | Lấy toàn bộ sơ đồ kho (Zone → Rack → Bin) cho FE vẽ 2D |

---

### ═══ MODULE 3: WMS — Sản phẩm & Danh mục ═══

> ⚠️ **Cập nhật sau review Mentor**: Trường `unit (String)` trong `ProductSku` đã được thay bằng FK `uom_id → UnitOfMeasure`. Xem thêm trong mục **"Thay Đổi Lớn Sau Review Mentor"** ở đầu file.

#### 3.1. Entities
- [x] **`UnitOfMeasure.java`** (MỚI — Dev A đã tạo) — `@Entity @Table("unit_of_measures")`, fields: `id (uuid)`, `name (varchar 100, not null)`, `symbol (varchar 20, not null)`, `type (varchar 50)` — ví dụ: WEIGHT, VOLUME, COUNT
- [x] **`ProductCategory.java`** — `@Entity @Table("product_categories")`, fields: `id (uuid)`, `tenant_id FK→User`, `name`, `defaultAttributes (jsonb)`, `createdAt`
- [x] **`ProductSku.java`** — `@Entity @Table("product_skus")`, fields: `id (uuid)`, `tenant_id FK→User`, `category_id FK→ProductCategory (null)`, `skuCode (unique)`, `name`, ~~`unit (String)`~~ **→ `uom_id FK→UnitOfMeasure (not null)`**, `specifications (jsonb)`, extend `BaseEntity`

#### 3.2. Repositories
- [x] **`UnitOfMeasureRepository.java`** (MỚI — Dev A đã tạo) — `findBySymbolIgnoreCase(String)`, `findAll()`
- [x] **`ProductCategoryRepository.java`** — `findAllByTenantId(UUID)`
- [x] **`ProductSkuRepository.java`** — `findAllByTenantId(UUID, Pageable)`, `findBySkuCodeAndTenantId(String, UUID)`, `existsBySkuCodeAndTenantId(String, UUID)`

#### 3.3. DTOs
- [x] **`ProductCategoryResponse.java`** — `id`, `name`, `defaultAttributes`
- [x] **`CreateCategoryRequest.java`** — `name`, `defaultAttributes (Map<String,Object>)`
- [x] **`ProductSkuResponse.java`** — `id`, `skuCode`, `name`, ~~`unit`~~ **→ `uomId`, `uomSymbol`, `uomName`**, `specifications`, `categoryName`
- [x] **`CreateSkuRequest.java`** — `categoryId`, `skuCode`, `name`, ~~`unit`~~ **→ `uomId (UUID, not null)`**, `specifications`
- [x] **`UpdateSkuRequest.java`**
- [x] **`PagedSkuResponse.java`**

#### 3.4. Services
- [x] **`ProductCategoryService.java`** — methods:
  - `getMyCategories(UUID tenantId)` → `List<ProductCategoryResponse>`
  - `createCategory(UUID tenantId, CreateCategoryRequest)` → `ProductCategoryResponse`
  - `deleteCategory(UUID tenantId, UUID categoryId)`
- [x] **`ProductSkuService.java`** — methods:
  - `getMySKUs(UUID tenantId, Pageable)` → `PagedSkuResponse`
  - `getSkuDetail(UUID tenantId, UUID skuId)` → `ProductSkuResponse`
  - `createSku(UUID tenantId, CreateSkuRequest)` → `ProductSkuResponse` — validate `skuCode` unique per tenant, validate `uomId` tồn tại
  - `updateSku(UUID tenantId, UUID skuId, UpdateSkuRequest)` → `ProductSkuResponse`
  - `deleteSku(UUID tenantId, UUID skuId)` — validate không có StockBatch đang dùng SKU này

#### 3.5. Controllers
- [x] **`TenantProductController.java`** — `@RequestMapping("/api/tenant/products")`, `@PreAuthorize("hasRole('TENANT')")`

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
  | `GET` | `/api/tenant/products/uoms` | Danh sách đơn vị tính (UOM) để hiển thị dropdown tạo SKU |

---

### ═══ MODULE 4: WMS — Phiếu Nhập/Xuất Kho ═══

> ⚠️ **Cập nhật sau review Mentor**: `InventoryReceipt` đã được bổ sung trường `referenceId (UUID, nullable)` để liên kết ngược về phiếu kiểm kê (`InventoryAudit`) khi phiếu nhập/xuất được sinh tự động từ quy trình phê duyệt kiểm kê.

#### 4.1. Entities
- [x] **`InventoryReceipt.java`** — `@Entity @Table("inventory_receipts")`, fields: `id (uuid)`, `warehouse_id FK→Warehouse`, `created_by FK→User`, `type (DocumentType: INBOUND/OUTBOUND)`, `signatureData (text)`, `status (ApprovalStatus)`, **`referenceId (UUID, nullable)`** — liên kết đến `InventoryAudit.id` nếu phiếu được sinh từ kiểm kê, `createdAt`
- [x] **`InventoryReceiptItem.java`** — `id`, `receipt_id FK`, `sku_id FK`, `quantity`, `zone_id FK (null)`, `rack_id FK (null)`, `bin_id FK (null)`, `note`

#### 4.2. Repository
- [x] **`InventoryReceiptRepository.java`** — `findByWarehouseIdAndType(UUID, DocumentType, Pageable)`, `findByCreatedBy(UUID, Pageable)`

#### 4.3. DTOs
- [x] **`CreateInventoryReceiptRequest.java`** — `warehouseId`, `type`, `items: List<{skuId, quantity, zoneId, rackId, binId, note}>`
- [x] **`InventoryReceiptResponse.java`** — đầy đủ thông tin phiếu + danh sách dòng hàng
- [x] **`PagedReceiptResponse.java`**

#### 4.4. Services
- [x] **`InventoryReceiptService.java`** — methods:
  - `createReceipt(UUID userId, CreateInventoryReceiptRequest)` → `InventoryReceiptResponse` 🔗 cập nhật `StockBatch`
  - `createAdjustmentReceipt(UUID userId, UUID auditId, UUID warehouseId, List<{skuId, quantity, binId}> items)` → `InventoryReceiptResponse` — **internal**, được gọi bởi `InventoryAuditService.approveAudit()` để sinh phiếu điều chỉnh tự động với `referenceId = auditId`
  - `approveReceipt(UUID staffId, UUID receiptId)` → `InventoryReceiptResponse`
  - `getReceiptsByWarehouse(UUID warehouseId, DocumentType, Pageable)` → `PagedReceiptResponse`

#### 4.5. Controllers
- [x] **`InventoryReceiptController.java`** — `@RequestMapping("/api/tenant/inventory/receipts")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/tenant/inventory/receipts` | Tạo phiếu nhập/xuất kho |
  | `GET` | `/api/tenant/inventory/receipts` | Danh sách phiếu (filter type, phân trang) |
  | `GET` | `/api/tenant/inventory/receipts/{id}` | Chi tiết phiếu |
  | `PATCH` | `/api/tenant/inventory/receipts/{id}/approve` | Xác nhận/duyệt phiếu |

---

## 🅱 DEV B — WMS Stock · Inventory Audit · Báo cáo tồn kho · Hook Notification

### ═══ MODULE 5: WMS — Lô Hàng Tồn Kho ═══

#### 5.1. Entity
- [ ] **`StockBatch.java`** — `@Entity @Table("stock_batches")`, fields: `id (uuid)`, `sku_id FK→ProductSku`, `warehouse_id FK→Warehouse`, `zone_id FK→WarehouseZone (null)`, `rack_id FK→WarehouseRack (null)`, `bin_id FK→WarehouseBin (null)` 🔗, `quantity (int, default 0)`, `arrivalDate`, extend `BaseEntity`

#### 5.2. Repository
- [ ] **`StockBatchRepository.java`** — `findBySkuIdAndWarehouseId(UUID, UUID)`, `findByWarehouseId(UUID, Pageable)`, `findByBinId(UUID)`, `sumQuantityBySkuId(UUID)` (native/jpql query)

#### 5.3. DTOs
- [ ] **`StockBatchResponse.java`** — `id`, `skuCode`, `skuName`, `uomSymbol` *(lấy từ `sku.getUom().getSymbol()`)*, `warehouseName`, `zoneName`, `rackName`, `binName`, `quantity`, `arrivalDate`
- [ ] **`PagedStockBatchResponse.java`**
- [ ] **`StockSummaryResponse.java`** — `skuId`, `skuCode`, `skuName`, `uomSymbol`, `totalQuantity`, `locations[]`

#### 5.4. Services
- [ ] **`StockBatchService.java`** — methods:
  - `getStockByWarehouse(UUID tenantId, UUID warehouseId, Pageable)` → `PagedStockBatchResponse`
  - `getStockSummaryBySku(UUID tenantId, UUID skuId)` → `StockSummaryResponse` — tổng hợp số lượng theo SKU
  - `adjustQuantity(UUID batchId, int delta)` — **internal**, gọi từ `InventoryReceiptService` khi approve phiếu INBOUND/OUTBOUND
  - `findOrCreateBatch(UUID skuId, UUID warehouseId, UUID zoneId, UUID rackId, UUID binId)` → `StockBatch` — **internal**

#### 5.5. Controllers
- [ ] **`StockBatchController.java`** — `@RequestMapping("/api/tenant/inventory/stock")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/tenant/inventory/stock` | Xem toàn bộ tồn kho trong kho mình đang thuê |
  | `GET` | `/api/tenant/inventory/stock/summary` | Tổng hợp tồn kho theo SKU |
  | `GET` | `/api/tenant/inventory/stock/sku/{skuId}` | Tồn kho chi tiết theo SKU + vị trí |

---

### ═══ MODULE 6: WMS — Kiểm Kê Kho ═══

> ⚠️ **THAY ĐỔI QUAN TRỌNG**: Module này đã **hoàn toàn thay thế** khái niệm "Phiếu Điều Chỉnh (`AdjustmentNote`)" theo yêu cầu của Mentor.
> Khi phát hiện sai lệch sau kiểm kê, hệ thống **tự động sinh phiếu nhập/xuất điều chỉnh** thay vì dùng một bảng riêng.

#### 6.1. Entities
- [ ] **`InventoryAudit.java`** — `@Entity @Table("inventory_audits")`, fields:
  - `id (uuid, PK)`
  - `warehouse_id FK→Warehouse (NOT NULL)`
  - `requested_by FK→User (NOT NULL)` — Tenant/Staff yêu cầu kiểm kê
  - `approved_by FK→User (null)` — Người duyệt kiểm kê
  - `status (AuditStatus: PENDING / SUBMITTED / APPROVED / REJECTED)` — xem Enum bên dưới
  - `note (text, null)` — ghi chú lý do kiểm kê
  - `createdAt`, extend `BaseEntity`

- [ ] **`InventoryAuditItem.java`** — `@Entity @Table("inventory_audit_items")`, fields:
  - `id (uuid, PK)`
  - `audit_id FK→InventoryAudit (NOT NULL)`
  - `batch_id FK→StockBatch (NOT NULL)`
  - `expectedQuantity (int)` — số lượng hệ thống đang ghi nhận tại thời điểm tạo phiếu
  - `actualQuantity (int)` — số lượng thực đếm được
  - `discrepancy (int, computed: actualQuantity - expectedQuantity)` — âm = thiếu, dương = thừa
  - `note (text, null)` — ghi chú cho dòng hàng cụ thể

- [ ] **`AuditStatus.java`** (Enum) — `PENDING`, `SUBMITTED`, `APPROVED`, `REJECTED`

#### 6.2. Repositories
- [ ] **`InventoryAuditRepository.java`** — `findByWarehouseId(UUID, Pageable)`, `findByRequestedBy(UUID, Pageable)`, `findByStatus(AuditStatus, Pageable)`
- [ ] **`InventoryAuditItemRepository.java`** — `findByAuditId(UUID)`, `findByBatchId(UUID)`

#### 6.3. DTOs
- [ ] **`CreateInventoryAuditRequest.java`** — `warehouseId`, `note`
- [ ] **`SubmitAuditRequest.java`** — `items: List<{batchId, actualQuantity, note}>`
- [ ] **`InventoryAuditItemResponse.java`** — `id`, `batchId`, `skuCode`, `skuName`, `uomSymbol`, `expectedQuantity`, `actualQuantity`, `discrepancy`, `note`
- [ ] **`InventoryAuditResponse.java`** — `id`, `warehouseId`, `warehouseName`, `status`, `note`, `requestedBy`, `approvedBy`, `createdAt`, `items: List<InventoryAuditItemResponse>`
- [ ] **`PagedAuditResponse.java`**

#### 6.4. Service
- [ ] **`InventoryAuditService.java`** — methods:
  - `createAudit(UUID userId, CreateInventoryAuditRequest)` → `InventoryAuditResponse`
    - Tạo phiếu kiểm kê với status `PENDING`.
    - Tự động snapshot `expectedQuantity` từ `StockBatch.quantity` hiện tại cho từng lô hàng trong kho.
  - `submitAudit(UUID userId, UUID auditId, SubmitAuditRequest)` → `InventoryAuditResponse`
    - Người dùng điền số lượng thực tế `actualQuantity` và tính toán `discrepancy` cho từng dòng.
    - Chuyển status sang `SUBMITTED`.
  - `approveAudit(UUID approverId, UUID auditId)` → `InventoryAuditResponse`
    - **Luồng chính:** Duyệt kiểm kê và tự động điều chỉnh tồn kho nếu có sai lệch.
    - Với mỗi `InventoryAuditItem` có `discrepancy != 0`:
      - Nếu `discrepancy > 0` (thừa): Tạo phiếu `InventoryReceipt` loại `INBOUND` điều chỉnh thông qua `inventoryReceiptService.createAdjustmentReceipt()` với `referenceId = auditId`.
      - Nếu `discrepancy < 0` (thiếu): Tạo phiếu `InventoryReceipt` loại `OUTBOUND` điều chỉnh tương tự.
    - Phiếu nhập/xuất tự động này sẽ được `InventoryReceiptService` xử lý và cập nhật `StockBatch.quantity` + ghi `InventoryTransaction`.
    - Chuyển status sang `APPROVED`.
  - `rejectAudit(UUID approverId, UUID auditId, String reason)` → `InventoryAuditResponse`
    - Từ chối, chuyển status sang `REJECTED`.
  - `getMyAudits(UUID userId, Pageable)` → `PagedAuditResponse`
  - `getAuditDetail(UUID userId, UUID auditId)` → `InventoryAuditResponse`

#### 6.5. Controller
- [ ] **`InventoryAuditController.java`** — `@RequestMapping("/api/tenant/inventory/audits")`, `@PreAuthorize("hasRole('TENANT')")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `POST` | `/api/tenant/inventory/audits` | Tạo phiếu kiểm kê mới |
  | `GET` | `/api/tenant/inventory/audits` | Danh sách phiếu kiểm kê (phân trang) |
  | `GET` | `/api/tenant/inventory/audits/{id}` | Chi tiết phiếu kiểm kê |
  | `POST` | `/api/tenant/inventory/audits/{id}/submit` | Nộp kết quả kiểm đếm thực tế |
  | `PATCH` | `/api/tenant/inventory/audits/{id}/approve` | Duyệt kiểm kê (tự động điều chỉnh tồn kho) |
  | `PATCH` | `/api/tenant/inventory/audits/{id}/reject` | Từ chối kiểm kê |

---

### ═══ MODULE 7: WMS — Giao dịch Tồn Kho (Audit Trail) ═══

> ⚠️ **THAY ĐỔI**: Cột `adjustment_id` đã bị **xóa**. Cột `receipt_id` bắt buộc `NOT NULL`.
> Mọi thay đổi số lượng kho phải đi qua `InventoryReceipt` — kể cả các thay đổi phát sinh từ kiểm kê.

#### 7.1. Entity
- [ ] **`InventoryTransaction.java`** — `@Entity @Table("inventory_transactions")`, fields:
  - `id (uuid, PK)`
  - `receipt_id FK→InventoryReceipt (NOT NULL)` — mọi giao dịch **bắt buộc** có phiếu nhập/xuất nguồn gốc (bao gồm cả phiếu tự động sinh từ kiểm kê)
  - `batch_id FK→StockBatch (NOT NULL)`
  - `quantityChanged (int)` — số dương: nhập thêm; số âm: xuất ra
  - `createdAt`

#### 7.2. Repository
- [ ] **`InventoryTransactionRepository.java`** — `findByBatchId(UUID, Pageable)`, `findByReceiptId(UUID)`

#### 7.3. Service (thêm method vào `InventoryReceiptService`)
- [ ] `recordTransaction(UUID receiptId, UUID batchId, int qty)` — **internal**, gọi sau khi approve phiếu nhập/xuất
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
- [ ] **`InventoryAuditService.approveAudit()`** → push cho User yêu cầu kiểm kê: `"Phiếu kiểm kê kho {warehouseName} đã được duyệt. Tồn kho đã được điều chỉnh tự động."`
- [ ] **`InventoryAuditService.rejectAudit()`** → push cho User yêu cầu kiểm kê: `"Phiếu kiểm kê kho {warehouseName} bị từ chối. Lý do: {reason}"`

---

### ═══ MODULE 9: Admin mở rộng cho WMS ═══

#### 9.1. Services & Controllers mới (thêm vào `admin/`)
- [ ] **`AdminInventoryController.java`** — `@RequestMapping("/api/admin/inventory")`

  | Method | Path | Mô tả |
  |--------|------|--------|
  | `GET` | `/api/admin/inventory/receipts` | Xem tất cả phiếu nhập/xuất kho toàn hệ thống |
  | `GET` | `/api/admin/inventory/audits` | Xem tất cả phiếu kiểm kê toàn hệ thống |
  | `GET` | `/api/admin/inventory/stock` | Tổng hợp tồn kho toàn hệ thống theo warehouse |

---

## 🔧 DÙNG CHUNG — Cả 2 Dev cần làm

### ErrorCode mới (thêm vào `ErrorCode.java`)

#### ✅ Dev A đã thêm (không cần làm lại)
- `NOTIFICATION_NOT_FOUND`
- `PRODUCT_CATEGORY_NOT_FOUND`, `SKU_NOT_FOUND`, `SKU_CODE_DUPLICATE`
- `INVENTORY_RECEIPT_NOT_FOUND`, `INVENTORY_RECEIPT_ALREADY_APPROVED`
- `WAREHOUSE_BIN_NOT_FOUND`
- `UOM_NOT_FOUND` *(mới thêm cùng với refactor UOM)*

#### ⬜ Dev B cần thêm
- **Stock:** `STOCK_BATCH_NOT_FOUND`, `STOCK_INSUFFICIENT_QUANTITY`
- **Audit:** `AUDIT_NOT_FOUND`, `AUDIT_ALREADY_PROCESSED`, `AUDIT_INVALID_STATUS`

---

## 💡 Conventions Cần Follow

### Package Structure WMS
```
wms/
  product/
    controller/     — TenantProductController
    dto/
    entity/         — ProductCategory, ProductSku, UnitOfMeasure
    repository/     — ProductCategoryRepository, ProductSkuRepository, UnitOfMeasureRepository
    service/        — ProductCategoryService, ProductSkuService
  stock/
    controller/     — StockBatchController, InventoryAuditController
    dto/
    entity/         — StockBatch, InventoryAudit, InventoryAuditItem, AuditStatus
    repository/     — StockBatchRepository, InventoryAuditRepository, InventoryAuditItemRepository
    service/        — StockBatchService, InventoryAuditService
  receipt/
    controller/     — InventoryReceiptController
    dto/
    entity/         — InventoryReceipt, InventoryReceiptItem, InventoryTransaction
    repository/     — InventoryReceiptRepository, InventoryTransactionRepository
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

### AuditStatus Enum (MỚI — Dev B tạo)
```java
public enum AuditStatus {
    PENDING,    // Vừa tạo, chưa điền kết quả
    SUBMITTED,  // Đã nộp kết quả kiểm đếm, chờ duyệt
    APPROVED,   // Đã duyệt, tồn kho đã được điều chỉnh
    REJECTED    // Bị từ chối
}
```

### Lưu ý khi build DTO liên quan đến SKU (QUAN TRỌNG cho Dev B)
```java
// SAI — unit không còn là String nữa
String unit = sku.getUnit();

// ĐÚNG — lấy từ thực thể UnitOfMeasure
String uomSymbol = sku.getUom().getSymbol(); // ví dụ: "kg", "cái", "thùng"
String uomName   = sku.getUom().getName();   // ví dụ: "Kilogram", "Cái", "Thùng"
```

---

## 🚀 Thứ Tự Implement Đề Xuất (Cập nhật)

| Ngày | Dev A | Dev B |
|------|-------|-------|
| 1 | ✅ `WarehouseBin` entity + repo (sync point) + `NotificationService` skeleton | ⬜ `StockBatch` entity + repo + service skeleton |
| 2 | ✅ `NotificationController` + Hook thông báo vào Warehouse/Inspection flow | ⬜ `InventoryAudit` + `InventoryAuditItem` entity + repo |
| 3 | ✅ `OwnerLayoutController` (Zone/Rack/Bin CRUD) + endpoint sơ đồ 2D | ⬜ `InventoryAuditService` (createAudit, submitAudit, approveAudit với auto-receipt) |
| 4 | ✅ `ProductCategoryService` + `ProductSkuService` + `TenantProductController` | ⬜ `InventoryAuditController` + `StockBatchController` |
| 5 | ✅ `InventoryReceipt` + `InventoryReceiptItem` entity + service | ✅ `InventoryTransaction` entity + audit trail service |
| 6 | ✅ `InventoryReceiptController` + wire update StockBatch | ⬜ Hook Notification vào Booking/Wallet/Contract + Audit flow |
| 7 | ✅ Integration test: luồng nhập/xuất kho đầy đủ | ⬜ `AdminInventoryController` |
| 8+ | ⬜ End-to-end test toàn bộ WMS Phase 2 | ⬜ Integration test: tồn kho + kiểm kê + thông báo end-to-end |
