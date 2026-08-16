# Tenant chấm dứt sớm hợp đồng kho

Tài liệu này mô tả luồng khi **Tenant chủ động chấm dứt một hợp đồng đang `ACTIVE`**.

## Quy tắc nghiệp vụ

- Tenant chủ động chấm dứt sớm được xem là vi phạm theo policy/hợp đồng đã ký.
- Khi Owner chấp thuận, **tiền cọc không được hoàn lại**. Cọc đã được ghi nhận cho Owner khi hợp đồng được kích hoạt, vì vậy luồng này không tạo giao dịch hoàn cọc mới.
- Owner chấp thuận không đồng nghĩa kho được trả ngay. Tenant phải xuất hết hàng và hai bên hoàn tất bàn giao trước khi kho được cho thuê lại.
- Sản phẩm, SKU và danh mục thuộc Tenant nên không bị xóa.
- Chứng từ nhập/xuất, tồn kho và lịch sử giao dịch được giữ lại để audit.

## State machine

```text
ACTIVE
  | Tenant POST /tenant-request-termination
  v
PENDING_TERMINATION
  | Owner agree=false ----------------------> ACTIVE
  |
  | Owner agree=true
  v
PENDING_HANDOVER  (chỉ xuất hàng; cọc bị tịch thu)
  | tồn kho > 0 -> không được xác nhận bàn giao
  |
  | Tenant và Owner cùng POST /confirm-handover
  v
CANCELLED  -> Warehouse AVAILABLE
```

`PENDING_TERMINATION` là trạng thái chờ Owner quyết định: Tenant vẫn xem tồn và tạo/duyệt phiếu nhập-xuất. Chỉ sau khi Owner đồng ý, hợp đồng mới vào `PENDING_HANDOVER` và chặn nhập hàng. Layout Tenant vẫn chỉ được lưu khi hợp đồng là `ACTIVE`.

## API

### 1. Tenant gửi yêu cầu chấm dứt

`POST /api/contracts/{contractId}/tenant-request-termination`

Permission: `CONTRACT_TENANT_MANAGE`

```json
{
  "reason": "Ngừng hoạt động kinh doanh",
  "evidenceImages": [
    "https://.../evidence-1.jpg"
  ]
}
```

Điều kiện: hợp đồng phải ở trạng thái `ACTIVE` và người gọi là Tenant của hợp đồng.

Kết quả:

- `status = PENDING_TERMINATION`
- `tenantTermination = true`
- gửi notification cho Owner.

### 2. Owner phản hồi

`POST /api/contracts/{contractId}/owner-respond-termination`

Permission: `CONTRACT_OWNER_MANAGE`

```json
{
  "agree": true
}
```

Khi `agree = true`:

- `status = PENDING_HANDOVER`
- `depositForfeited = true`
- reset xác nhận bàn giao của hai bên về `false`
- không gọi `WalletService.refundBalance(...)`
- warehouse vẫn là `RENTED`
- gửi notification cho Tenant.

Khi `agree = false`, hợp đồng quay về `ACTIVE`; yêu cầu chấm dứt không làm mất cọc.

### 3. Xác nhận bàn giao

`PATCH /api/contracts/{contractId}/confirm-handover`

Permission: `CONTRACT_HANDOVER_CONFIRM`

Điều kiện:

- hợp đồng phải là `PENDING_HANDOVER`;
- tổng tồn kho trong warehouse phải bằng 0;
- Tenant và Owner phải xác nhận riêng.

Khi cả hai đã xác nhận:

- hợp đồng chuyển `CANCELLED`;
- booking chuyển `CANCELLED` với lý do cọc bị tịch thu;
- warehouse chuyển `AVAILABLE`;
- các staff assignment đang `ACTIVE` tại warehouse chuyển `REVOKED`;
- không có hoàn cọc.

## Ảnh hưởng tới từng module

| Module | Hành vi trong `PENDING_HANDOVER` |
| --- | --- |
| Contract | Giữ cọc, chờ hai bên xác nhận bàn giao; không hủy ngay. |
| Wallet | Không phát sinh hoàn cọc. Cọc đã được ghi nhận cho Owner khi kích hoạt hợp đồng. |
| Warehouse | Vẫn `RENTED` cho đến khi bàn giao hoàn tất; chỉ sau đó mới `AVAILABLE`. |
| Inventory receipt | Chỉ `OUTBOUND` được tạo/duyệt; `INBOUND` bị từ chối, kể cả phiếu nhập đã tạo trước đó nhưng chưa duyệt. |
| Stock batch / dashboard | Tenant vẫn xem được tồn kho để xuất hàng và đối soát. Tồn > 0 chặn bàn giao. |
| Product / SKU / category | Không xóa, vì dữ liệu thuộc Tenant, không thuộc một warehouse hay hợp đồng. |
| Layout | Tenant không còn hợp đồng `ACTIVE` nên không thể lưu/chỉnh layout. Layout clone hiện được giữ lại để audit; chưa có job archive/xóa clone. |
| Staff | Giữ quyền trong thời gian xuất hàng; bị revoke khi bàn giao hoàn tất. |
| Notification | Owner nhận yêu cầu chấm dứt, Tenant nhận quyết định của Owner. |

## Các file đã thay đổi

| Khu vực | File | Vai trò |
| --- | --- | --- |
| Contract API | `contract/controller/ContractController.java` | Hai endpoint request/response mới. |
| Contract service | `contract/service/ContractService.java` | State transition, tịch thu cọc, điều kiện tồn kho, bàn giao và revoke staff. |
| Contract model | `contract/entity/ContractStatus.java`, `RentalContract.java` | Thêm `PENDING_TERMINATION`, `tenantTermination`, `depositForfeited`. |
| Contract response | `contract/dto/RentalContractResponse.java` | Trả hai cờ mới cho frontend. |
| WMS receipt | `wms/receipt/service/InventoryReceiptService.java` | Chặn inbound và cho outbound khi thanh lý. |
| Stock access | `wms/stock/service/StockBatchService.java`, `StockBatchRepository.java` | Cho phép xem tồn trong giai đoạn thanh lý, đồng thời kiểm tra kho đã hết hàng. |
| Warehouse access | `contract/repository/RentalContractRepository.java` | Coi `PENDING_TERMINATION` và `PENDING_HANDOVER` là warehouse còn đang do Tenant sử dụng. |
| Staff | `staff/repository/StaffWarehouseAssignmentRepository.java` | Lấy assignment để revoke sau bàn giao. |
| Migration | `ops/migrations/20260815_tenant_termination_flow.sql` | Thêm cột và cập nhật DB constraint của trạng thái hợp đồng. |

## Response cần cho frontend

`RentalContractResponse` có thêm:

```json
{
  "status": "PENDING_HANDOVER",
  "tenantTermination": true,
  "depositForfeited": true,
  "depositAmount": 1000000,
  "cancelReason": "Ngừng hoạt động kinh doanh"
}
```

Gợi ý UI:

- `PENDING_TERMINATION`: Tenant hiển thị “Đang chờ Owner phản hồi”; Owner có nút đồng ý/từ chối.
- `PENDING_HANDOVER`: hiển thị cảnh báo “Cọc đã bị tịch thu”; chỉ hiển thị thao tác xuất hàng, kiểm kê và xác nhận bàn giao.
- vô hiệu hóa mọi nút tạo/duyệt phiếu nhập và chỉnh layout.
- chỉ hiển thị nút “Hoàn tất bàn giao” khi dashboard tồn kho bằng 0.

## Migration và triển khai

Trước khi deploy ứng dụng, chạy migration sau theo quy trình deploy database của môi trường:

```text
ops/migrations/20260815_tenant_termination_flow.sql
```

Migration thêm hai cột `tenant_termination`, `deposit_forfeited` (mặc định `false`) và cho phép status `PENDING_TERMINATION`.

## Test

Test nghiệp vụ chính nằm tại:

```text
src/test/java/fu/stockspace/stockspace_be/contract/service/ContractServiceTenantTerminationTest.java
```

Các case được kiểm tra:

1. Owner đồng ý chuyển hợp đồng sang `PENDING_HANDOVER`, giữ cọc, không hoàn tiền và không mở kho.
2. Handover bị chặn nếu warehouse vẫn còn tồn kho.

Chạy test liên quan trên Windows PowerShell:

```powershell
.\mvnw.cmd '-Dtest=ContractServiceTenantTerminationTest,InventoryReceiptServiceTest,StockBatchServiceTest' test
```

## Lưu ý vận hành

- API receipt hiện vẫn yêu cầu Tenant có subscription đang hoạt động. Nếu business cho phép Tenant xuất hàng để thanh lý dù subscription đã hết hạn, cần bổ sung ngoại lệ rõ ràng cho `OUTBOUND` trong `PENDING_HANDOVER`.
- Luồng này chỉ áp dụng cho **Tenant chủ động chấm dứt**. Không dùng lại nó cho Owner hủy hợp đồng, tranh chấp, hoặc bất khả kháng vì chính sách cọc có thể khác.
- Nếu cần tính thêm phí phạt ngoài cọc, cần bổ sung một settlement/invoice riêng. Không nên tự động trừ thêm tiền từ ví chỉ dựa trên trạng thái chấm dứt.
