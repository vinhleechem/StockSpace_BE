# Inventory Audit Lifecycle API

Tài liệu này mô tả đúng API và lifecycle hiện tại của tính năng Inventory Audit. FE sử dụng các endpoint bên dưới; không gọi trực tiếp receipt API để điều chỉnh tồn kho khi approve audit.

## 1. Quy ước chung

- Base path: `/api/tenant/inventory/audits`
- Authentication: `Authorization: Bearer <access-token>`.
- Tất cả endpoint yêu cầu permission `INVENTORY_AUDIT_MANAGE`.
- ID dùng UUID.
- Response thành công dùng envelope:

```json
{
  "success": true,
  "message": "...",
  "data": {}
}
```

- Response lỗi dùng envelope:

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "..."
}
```

FE nên dùng `success`, `code` và `data.status` để điều khiển UI; không suy diễn trạng thái từ message tiếng Việt.

## 2. Lifecycle và quyền

```text
PENDING --submit--> SUBMITTED --approve--> APPROVED
    |                    |
    +--reject----------> REJECTED
```

| Trạng thái | Ý nghĩa | Stock effect |
|---|---|---|
| `PENDING` | Phiếu đã tạo, đang chờ nhập số lượng thực tế | Chưa thay đổi |
| `SUBMITTED` | Đã gửi kết quả kiểm kê, chờ Tenant duyệt | Chưa thay đổi |
| `APPROVED` | Tenant đã duyệt | Điều chỉnh tồn kho theo discrepancy, tối đa một lần |
| `REJECTED` | Phiếu bị từ chối | Không thay đổi |

| Action | Tenant | Staff |
|---|---|---|
| Create audit | Được phép nếu có active contract và active subscription | Được phép nếu có active contract, subscription và active assignment tại warehouse |
| List/detail | Chỉ dữ liệu audit thuộc tenant và warehouse có quyền truy cập | Chỉ dữ liệu thuộc tenant và warehouse đang được assignment cho phép |
| Submit | Được phép khi audit ở `PENDING` | Được phép khi audit ở `PENDING` và assignment còn `ACTIVE` |
| Approve/reject | Được phép | Không được phép |

Sau khi Staff bị revoke assignment, Staff không thể submit hoặc xem audit theo quyền warehouse hiện tại. Audit do Staff đó tạo vẫn được xác định thuộc tenant cũ để Tenant của tenant đó tiếp tục xem và xử lý.

## 3. Endpoints

### 3.1 Create audit

```http
POST /api/tenant/inventory/audits
Content-Type: application/json
```

```json
{
  "warehouseId": "11111111-1111-1111-1111-111111111111",
  "note": "Monthly inventory count"
}
```

`warehouseId` bắt buộc. `note` tùy chọn.

Khi tạo thành công, backend snapshot các stock batch đang `ACTIVE`, chưa bị xóa và thuộc đúng tenant trong warehouse đó. Stock không thay đổi.

### 3.2 List audits

```http
GET /api/tenant/inventory/audits?page=0&size=10
GET /api/tenant/inventory/audits?warehouseId=11111111-1111-1111-1111-111111111111&page=0&size=10
```

`page` bắt đầu từ `0`, mặc định `0`; `size` mặc định `10`. Kết quả được sắp xếp `createdAt` giảm dần.

```json
{
  "success": true,
  "message": "Lấy danh sách phiếu kiểm kê thành công",
  "data": {
    "content": [],
    "page": 0,
    "size": 10,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  }
}
```

Nếu truyền `warehouseId`, FE phải truyền ID của warehouse đang được chọn. Không dùng kết quả của một warehouse để hiển thị như tồn kho tổng của toàn tenant.

### 3.3 Get audit detail

```http
GET /api/tenant/inventory/audits/{auditId}
```

Detail có cùng shape `InventoryAuditResponse` như item trong list, nhưng bao gồm đầy đủ `items`.

### 3.4 Submit actual quantities

```http
POST /api/tenant/inventory/audits/{auditId}/submit
Content-Type: application/json
```

```json
{
  "items": [
    {
      "batchId": "22222222-2222-2222-2222-222222222222",
      "actualQuantity": 85,
      "note": "Counted at rack A1"
    }
  ]
}
```

Rules:

- `items` không được rỗng.
- `batchId` bắt buộc.
- `actualQuantity` là số nguyên không âm.
- `batchId` phải lấy từ item của audit detail; FE không tự tạo batch ID.
- Audit phải đang ở `PENDING`.
- Backend tính `discrepancy = actualQuantity - expectedQuantity`.
- Thành công chuyển state sang `SUBMITTED`; stock chưa thay đổi.

### 3.5 Approve audit

```http
PATCH /api/tenant/inventory/audits/{auditId}/approve
```

Không có request body.

Audit phải ở `SUBMITTED` và người gọi phải là Tenant sở hữu audit. Backend xử lý từng discrepancy:

| Discrepancy | Internal effect |
|---:|---|
| `> 0` | Tạo adjustment `INBOUND` với quantity bằng discrepancy |
| `< 0` | Tạo adjustment `OUTBOUND` với quantity bằng giá trị tuyệt đối |
| `0` hoặc `null` | Không tạo adjustment receipt |

Sau khi xử lý thành công, audit chuyển sang `APPROVED`. Adjustment receipt và inventory transaction được tạo trong cùng workflow với audit; FE không gọi thêm endpoint receipt để “cộng/trừ lần nữa”.

### 3.6 Reject audit

```http
PATCH /api/tenant/inventory/audits/{auditId}/reject
Content-Type: application/json
```

Body có thể bỏ trống hoặc gửi reason:

```json
{
  "reason": "Please recount the items at rack A1"
}
```

Audit ở `PENDING` hoặc `SUBMITTED` mới được reject. Reject không thay đổi stock. `reason` là tùy chọn ở backend; FE nên gửi nếu UI có lý do từ chối.

## 4. Response data shape

`data` của create/detail/action là `InventoryAuditResponse`:

```json
{
  "id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "warehouseId": "11111111-1111-1111-1111-111111111111",
  "warehouseName": "Warehouse A",
  "status": "SUBMITTED",
  "note": "Monthly inventory count",
  "requestedById": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
  "requestedByName": "Tenant User",
  "approvedById": null,
  "approvedByName": null,
  "createdAt": "2026-08-27T09:00:00",
  "updatedAt": "2026-08-27T10:00:00",
  "items": [
    {
      "id": "cccccccc-cccc-cccc-cccc-cccccccccccc",
      "batchId": "22222222-2222-2222-2222-222222222222",
      "skuCode": "SKU-001",
      "skuName": "Product 1",
      "uomSymbol": "pcs",
      "rackName": "Rack A1",
      "binName": "Bin 01",
      "expectedQuantity": 100,
      "actualQuantity": 85,
      "discrepancy": -15,
      "note": "Counted at rack A1"
    }
  ]
}
```

Notes:

- `actualQuantity`, `discrepancy`, `approvedById`, `approvedByName` có thể là `null` trước khi submit/approve.
- `items` có thể rỗng nếu warehouse chưa có stock batch phù hợp.
- `discrepancy` âm nghĩa là thiếu hàng; dương nghĩa là thừa hàng.
- `createdAt` và `updatedAt` là datetime ISO-8601 do backend trả về.

## 5. Error handling hiện tại

| HTTP | Code | Khi nào FE thường gặp |
|---:|---|---|
| `400` | `AUDIT_INVALID_STATUS` | Submit audit không ở `PENDING`, hoặc approve không ở `SUBMITTED` |
| `400` | `AUDIT_ALREADY_PROCESSED` | Reject audit đã ở `APPROVED`/`REJECTED` |
| `400` | validation error | Thiếu `warehouseId`, `items` rỗng, thiếu `batchId`, hoặc actual quantity âm |
| `400` | `STOCK_INSUFFICIENT_QUANTITY` | Điều chỉnh outbound lớn hơn stock hiện có |
| `403` | `FORBIDDEN` | Sai tenant, Staff bị revoke assignment, Staff gọi approve/reject, hoặc thiếu quyền warehouse |
| `403` | `SUBSCRIPTION_REQUIRED` | Mutation khi tenant không có subscription active |
| `404` | `AUDIT_NOT_FOUND` | Audit không tồn tại, đã bị xóa, hoặc không tìm thấy theo ID |
| `404` | `WAREHOUSE_NOT_FOUND` | Warehouse không tồn tại khi create |
| `404` | `STOCK_BATCH_NOT_FOUND` | Batch được tham chiếu không còn tồn tại |

Retry action phải dựa trên response mới nhất. Không retry mù một request approve khi request trước đã thành công; backend khóa audit row và một audit đã `APPROVED` không được approve lần hai.

## 6. Traceability của stock adjustment

Khi approve tạo adjustment, backend giữ liên kết:

```text
InventoryAudit.id
        ↓ referenceId
InventoryReceipt.id
        ↓ receipt_id
InventoryTransaction
        ↓ batch_id
StockBatch.quantity
```

FE có thể dùng audit detail để hiển thị discrepancy. Khi cần kiểm tra lịch sử stock, dùng batch transaction API hiện có; không tạo receipt thủ công để thay thế adjustment của audit.

## 7. Dataset/demo fixture lặp lại được

Dùng một dataset cố định trên môi trường dev/staging, không chỉnh database thủ công giữa các lần demo:

1. Một Tenant có subscription active và active contract với `DEMO_WAREHOUSE_ID`.
2. Warehouse có ít nhất một SKU active của chính Tenant và một stock batch active tại rack/bin.
3. Lưu lại `TENANT_TOKEN`, `STAFF_TOKEN` (nếu demo Staff), `DEMO_WAREHOUSE_ID`, `DEMO_BATCH_ID` và `DEMO_AUDIT_ID` từ response.
4. Tenant hoặc Staff gọi create audit.
5. Người submit gọi detail, lấy `batchId` từ `data.items`, rồi submit actual quantity.
6. Tenant gọi detail để kiểm tra `SUBMITTED`, sau đó approve hoặc reject.
7. Nếu demo approve với `expectedQuantity = 100` và `actualQuantity = 85`, kiểm tra discrepancy `-15`; stock giảm 15 và lịch sử có adjustment outbound tham chiếu audit.
8. Không dùng lại audit đã `APPROVED`/`REJECTED` cho happy path. Tạo một audit mới từ stock batch hiện tại cho lần chạy tiếp theo.

FE nên giữ ID ở state của từng audit, không hard-code `auditId` hoặc `batchId` trong UI. Các nút action nên được bật/tắt theo `data.status`: submit chỉ ở `PENDING`, approve/reject chỉ ở `SUBMITTED` và chỉ render cho Tenant.
