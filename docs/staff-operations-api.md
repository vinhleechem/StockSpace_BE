# Staff Operations API

Tài liệu này mô tả contract hiện tại của backend cho màn hình Staff Operations/Tasks. Dữ liệu được tổng hợp trực tiếp từ các nghiệp vụ WMS đang có; backend không có entity `StaffTask`, không lưu task giả và không tạo lại các API mutation đã tồn tại.

## 1. Quy tắc chung

- Tất cả endpoint yêu cầu `Authorization: Bearer <access-token>`.
- Tenant hiện tại được lấy từ JWT/request context. FE không truyền `tenantId` để chọn tenant khác.
- Staff chỉ đọc hoặc thao tác trên warehouse đang có assignment `ACTIVE`, thuộc tenant hiện tại và còn contract `ACTIVE`.
- Sau khi assignment bị revoke, quyền bị thu hồi ngay ở các request tiếp theo. Assignment không bị xóa; `/api/staff/my-work-history` vẫn trả lịch sử.
- `403` là kết quả expected khi Staff gọi warehouse không được assign hoặc gọi action chỉ dành cho Tenant.
- Response thành công dùng envelope chung:

```json
{
  "success": true,
  "message": "...",
  "data": {}
}
```

## 2. Endpoint tổng hợp cho màn hình Staff Operations

### `GET /api/staff/operations`

Đây là endpoint read-only để hiển thị các operation cần xử lý. Endpoint không tạo bảng task và không thay thế Receipt/Audit/Transfer API.

Query parameters:

| Parameter | Required | Mô tả |
|---|---:|---|
| `warehouseId` | No | Lọc theo một warehouse đang được Staff assign. Nếu không truyền, lấy tất cả warehouse được assign và còn contract. |
| `type` | No | `RECEIPT`, `AUDIT` hoặc `TRANSFER`. Không truyền thì lấy cả ba loại. |
| `status` | No | Lọc exact theo enum/status dạng chữ hoa, ví dụ `PENDING`, `SUBMITTED`, `IN_TRANSIT`. |
| `page` | No | Page bắt đầu từ `0`, mặc định `0`. |
| `size` | No | Mặc định `20`. |

Khi không truyền `status`, backend trả các operation đang cần xử lý:

- Receipt: `PENDING`.
- Audit: `PENDING`, `SUBMITTED`.
- Transfer: `PENDING`, `IN_TRANSIT`.

Ví dụ:

```http
GET /api/staff/operations?warehouseId={warehouseId}&page=0&size=20
Authorization: Bearer <access-token>
```

Response mẫu:

```json
{
  "success": true,
  "message": "Lấy danh sách operation của Staff thành công",
  "data": {
    "content": [
      {
        "operationType": "AUDIT",
        "operationId": "audit-uuid",
        "warehouseId": "warehouse-a-uuid",
        "warehouseName": "Warehouse A",
        "sourceWarehouseId": null,
        "sourceWarehouseName": null,
        "destinationWarehouseId": null,
        "destinationWarehouseName": null,
        "status": "PENDING",
        "createdAt": "2026-08-27T12:00:00",
        "allowedActions": ["VIEW", "SUBMIT"]
      },
      {
        "operationType": "TRANSFER",
        "operationId": "transfer-uuid",
        "warehouseId": "warehouse-a-uuid",
        "warehouseName": "Warehouse A",
        "sourceWarehouseId": "warehouse-a-uuid",
        "sourceWarehouseName": "Warehouse A",
        "destinationWarehouseId": "warehouse-b-uuid",
        "destinationWarehouseName": "Warehouse B",
        "status": "IN_TRANSIT",
        "createdAt": "2026-08-27T11:00:00",
        "allowedActions": ["VIEW"]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "last": true
  }
}
```

`operationType` và `operationId` là cặp khóa để FE điều hướng đến màn hình chi tiết tương ứng:

| `operationType` | Nguồn dữ liệu | `operationId` |
|---|---|---|
| `RECEIPT` | `InventoryReceipt` | Receipt id |
| `AUDIT` | `InventoryAudit` | Audit id |
| `TRANSFER` | `StockTransfer` | Transfer id |

`allowedActions` chỉ là metadata để FE hiển thị nút phù hợp. Backend vẫn là nơi quyết định quyền cuối cùng; FE không được dùng field này để bỏ qua authorization.

## 3. API Staff sử dụng để xem chi tiết và thực hiện operation

### 3.1 Receipt

| Method | Endpoint | Mục đích | Staff |
|---|---|---|---|
| `GET` | `/api/tenant/inventory/receipts?warehouseId={id}&type=INBOUND&page=0&size=10` | Danh sách receipt theo kho | Được xem nếu được assign |
| `GET` | `/api/tenant/inventory/receipts/{receiptId}` | Chi tiết receipt | Được xem nếu có quyền theo kho |
| `POST` | `/api/tenant/inventory/receipts` | Tạo receipt ở `PENDING` | Được tạo ở kho được assign |
| `PATCH` | `/api/tenant/inventory/receipts/{receiptId}/approve` | Tenant duyệt receipt và cập nhật tồn kho | Staff bị từ chối |
| `PATCH` | `/api/tenant/inventory/receipts/{receiptId}/reject` | Tenant từ chối receipt | Staff bị từ chối |

Request tạo receipt:

```json
{
  "warehouseId": "warehouse-uuid",
  "type": "INBOUND",
  "signatureData": "optional-signature",
  "items": [
    {
      "skuId": "sku-uuid",
      "quantity": 10,
      "rackId": "rack-uuid",
      "binId": "bin-uuid",
      "note": "optional"
    }
  ]
}
```

`type` dùng enum hiện tại của backend, ví dụ `INBOUND` hoặc `OUTBOUND`. Các field `skuId`, `quantity`, `rackId`, `binId` cần gửi theo validation của request.

### 3.2 Inventory Audit

| Method | Endpoint | Mục đích | Staff |
|---|---|---|---|
| `GET` | `/api/tenant/inventory/audits?warehouseId={id}&page=0&size=10` | Danh sách audit | Được xem nếu được assign |
| `GET` | `/api/tenant/inventory/audits/{auditId}` | Chi tiết audit | Được xem nếu được assign |
| `POST` | `/api/tenant/inventory/audits` | Tạo audit cho kho | Được tạo nếu được assign |
| `POST` | `/api/tenant/inventory/audits/{auditId}/submit` | Gửi actual quantity | Được submit audit được phép |
| `PATCH` | `/api/tenant/inventory/audits/{auditId}/approve` | Tenant duyệt và điều chỉnh tồn | Staff bị từ chối |
| `PATCH` | `/api/tenant/inventory/audits/{auditId}/reject` | Tenant từ chối audit | Staff bị từ chối |

Request tạo audit:

```json
{
  "warehouseId": "warehouse-uuid",
  "note": "optional"
}
```

Request submit audit:

```json
{
  "items": [
    {
      "batchId": "batch-uuid",
      "actualQuantity": 8,
      "note": "optional"
    }
  ]
}
```

### 3.3 Stock Transfer

Transfer chỉ dành cho hai warehouse của cùng tenant. Staff phải có assignment `ACTIVE` ở cả warehouse nguồn và warehouse đích để tạo/xem transfer liên quan.

| Method | Endpoint | Mục đích | Staff |
|---|---|---|---|
| `GET` | `/api/tenant/inventory/transfers?sourceWarehouseId={id}&destinationWarehouseId={id}&status=PENDING&page=0&size=10` | Danh sách transfer | Chỉ thấy transfer nằm trong phạm vi kho được assign |
| `GET` | `/api/tenant/inventory/transfers/{transferId}` | Chi tiết transfer | Cần assignment ở cả hai đầu |
| `POST` | `/api/tenant/inventory/transfers` | Tạo yêu cầu transfer ở `PENDING` | Cần assignment ở nguồn và đích |
| `PATCH` | `/api/tenant/inventory/transfers/{transferId}/approve-dispatch` | Tenant duyệt xuất, chuyển `IN_TRANSIT` | Staff bị từ chối |
| `POST` | `/api/tenant/inventory/transfers/{transferId}/receive` | Tenant nhận hàng ở kho đích | Staff bị từ chối |
| `PATCH` | `/api/tenant/inventory/transfers/{transferId}/reject` | Tenant từ chối | Staff bị từ chối |
| `PATCH` | `/api/tenant/inventory/transfers/{transferId}/cancel` | Tenant hủy yêu cầu | Staff bị từ chối |

Request tạo transfer:

```json
{
  "sourceWarehouseId": "warehouse-a-uuid",
  "destinationWarehouseId": "warehouse-b-uuid",
  "note": "optional",
  "items": [
    {
      "skuId": "sku-uuid",
      "requestedQuantity": 5,
      "sourceAllocations": [
        {
          "sourceStockBatchId": "batch-uuid",
          "sourceRackId": "rack-uuid",
          "sourceBinId": "bin-uuid",
          "quantity": 5
        }
      ]
    }
  ]
}
```

Staff không được tự gọi mutation của Tenant chỉ vì role Staff đang có permission `INVENTORY_UPDATE` trong default RBAC. Service layer kiểm tra role và từ chối các action approve/reject/cancel/receive.

## 4. Stock, layout và capacity lookup

Các endpoint dưới đây là read-only và dùng lại module hiện có:

| Method | Endpoint | Phạm vi Staff |
|---|---|---|
| `GET` | `/api/tenant/inventory/stock?warehouseId={id}&page=0&size=20` | Chỉ stock của warehouse được assign |
| `GET` | `/api/tenant/inventory/stock/overview?warehouseId={id}&page=0&size=20` | Chỉ overview của warehouse được assign |
| `GET` | `/api/tenant/inventory/stock/sku/{skuId}` | Chỉ các location thuộc warehouse được assign |
| `GET` | `/api/tenant/inventory/stock/summary?skuId={skuId}` | Tổng hợp theo các warehouse được assign |
| `GET` | `/api/tenant/warehouses/{warehouseId}/layout/capacity` | Capacity của warehouse được assign |
| `GET` | `/api/staff/warehouses/{warehouseId}/layout` | Layout tree read-only của warehouse được assign |
| `POST` | `/api/tenant/inventory/putaway/suggestions` | Chỉ xử lý location trong warehouse được assign |

FE phải luôn truyền warehouse đang chọn vào endpoint có `warehouseId`. Không dùng một list tồn kho tổng của toàn tenant để hiển thị như tồn kho của một warehouse.

## 5. Work history và warehouse selector

### `GET /api/staff/my-work-history`

Trả thông tin staff, các tenant tenure và toàn bộ warehouse assignment theo lịch sử. `warehouseAssignments` có thể chứa `ACTIVE`, `REVOKED` hoặc status lịch sử khác; FE không được dùng toàn bộ list này như danh sách kho đang làm việc.

Để tạo warehouse selector hiện tại, chỉ dùng assignment có:

- `status === "ACTIVE"`;
- assignment chưa bị xóa;
- contract của tenant với warehouse còn active (backend tiếp tục kiểm tra khi gọi API).

## 6. Permission và authorization matrix

| Chức năng | Endpoint permission gate | Staff được phép? | Điều kiện bổ sung |
|---|---|---:|---|
| Xem operation tổng hợp | `STAFF_WORK_HISTORY_READ` | Có | Assignment ACTIVE + contract ACTIVE |
| Xem stock/overview/summary | `INVENTORY_READ` | Có | Đúng warehouse được assign |
| Xem layout/capacity | `INVENTORY_READ` | Có | Đúng warehouse được assign |
| Tạo receipt | `INBOUND_CREATE`/`OUTBOUND_CREATE` | Có | Đúng warehouse được assign |
| Tạo/submit audit | `INVENTORY_AUDIT_MANAGE` | Có | Đúng warehouse được assign |
| Tạo transfer | `INVENTORY_CREATE` | Có | Assignment ở cả source và destination |
| Approve/reject receipt | `INVENTORY_UPDATE` | Không | Tenant only |
| Approve/reject audit | `INVENTORY_AUDIT_MANAGE` | Không | Tenant only |
| Dispatch/receive/reject/cancel transfer | `INVENTORY_UPDATE` | Không | Tenant only |

Các permission là lớp kiểm tra đầu tiên. Quyền theo warehouse và vai trò nghiệp vụ được kiểm tra tiếp trong service; không coi việc endpoint có permission là đủ để Staff thực hiện quyết định của Tenant.

## 7. Cách FE tích hợp

1. Gọi `/api/staff/my-work-history`, lọc assignment `ACTIVE` để tạo danh sách warehouse.
2. Gọi `/api/staff/operations` cho màn hình Staff Operations; render theo `operationType`, `status` và `allowedActions`.
3. Khi người dùng mở chi tiết, gọi API chi tiết của module tương ứng bằng `operationId`.
4. Khi Staff submit audit hoặc tạo receipt/transfer, gọi API mutation gốc của module; không tạo endpoint `/staff/tasks` và không lưu task ở FE/backend.
5. Nếu API trả `403`, refresh lại assignment/operation list và ẩn action hoặc warehouse không còn quyền.
6. Test tối thiểu với hai warehouse: Staff được assign A nhưng không assign B phải xem/thao tác được A và nhận `403` với B; sau revoke phải refresh token hoặc request mới và không còn truy cập được A.

## 8. Ngoài phạm vi Plan 07

Không triển khai task board, deadline/SLA, comment, notification riêng cho task, workflow approval mới, permission builder hoặc một hệ thống assignment cá nhân. Nếu cần các chức năng đó, phải lập business requirement và API design riêng.
