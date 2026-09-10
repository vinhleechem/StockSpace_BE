# Stock Transfer V2 — FE/Codex handoff

Tài liệu ngắn để FE và agent Codex dùng chung contract chuyển kho.

Base URL: `/api/tenant/inventory/transfers`

## 1. Những gì đã thêm

- Tạo transfer có thể gán `sourceStaffId` để pick và `destinationStaffId` để nhận.
- Luồng chuẩn: reservation → pick → duyệt xuất → vận chuyển → nhận hàng.
- Nhận nhiều đợt, nhận thiếu, hàng hỏng/quarantine và reconcile.
- Timeline event có actor, lý do, timestamp, idempotency key.
- SLA `expectedArrivalAt`; quá hạn chuyển `OVERDUE`, không tự đổi tồn.
- Retry/chuyển tiếp nhiều lần và return về kho nguồn qua các `attempt` độc lập.
- Migration thêm counter tồn, reservation, pick line, event, command và attempt.

Transfer luôn thuộc cùng một tenant, source và destination phải khác nhau.
`destinationWarehouse` là tuyến gốc; retry chỉ đổi
`currentDestinationWarehouse`, không sửa tuyến gốc.

## 2. Luồng trạng thái

```text
PENDING
  -> ALLOCATED -> PICKING -> READY_TO_DISPATCH
  -> IN_TRANSIT -> ARRIVED_AT_DESTINATION -> RECEIVING
       -> COMPLETED                 (nhận đủ GOOD)
       -> PARTIALLY_RECEIVED        (nhận thiếu, có thể receive tiếp)
       -> RECONCILING               (đủ vật lý nhưng có non-GOOD)

RECEIVE_REJECTED / SHORT_RECEIVED
  -> RETRY_REQUESTED -> IN_TRANSIT (retry sang kho khác)
  -> RETURN_REQUESTED -> RETURN_IN_TRANSIT -> RETURNED/PARTIALLY_RETURNED

RECONCILING -> COMPLETED | LOST | RETURN_REQUESTED
```

`OVERDUE` có thể xuất hiện từ `IN_TRANSIT`, `ARRIVED_AT_DESTINATION` hoặc
`PARTIALLY_RECEIVED`. Khi overdue, FE cho operator xác minh rồi `receive` hoặc
`reject-receipt`; muốn quay đầu thì reject trước, sau đó request return.

| Status | FE hiểu là | Action chính |
|---|---|---|
| `PENDING` | Chưa giữ hàng | allocate, approve-dispatch, reject, cancel |
| `ALLOCATED` | Đã giữ hàng trên source | pick, approve-dispatch, cancel |
| `PICKING` | Đang scan | pick tiếp |
| `READY_TO_DISPATCH` | Pick đủ | approve-dispatch |
| `IN_TRANSIT` | Source đã trừ tồn | arrive, receive, reject-receipt |
| `OVERDUE` | Quá SLA | receive hoặc reject-receipt |
| `ARRIVED_AT_DESTINATION`/`RECEIVING` | Đang nhận hàng | receive, reject-receipt |
| `PARTIALLY_RECEIVED` | Đã nhận thiếu | receive tiếp, close-short |
| `SHORT_RECEIVED` | Đã chốt thiếu | retry, request-return |
| `RECEIVE_REJECTED` | Kho đích từ chối toàn bộ | retry, request-return |
| `RECONCILING` | Có hàng non-GOOD | reconcile |
| `RETRY_REQUESTED`/`RETURN_REQUESTED` | Chờ dispatch chặng mới | dispatch tương ứng |
| `RETURN_IN_TRANSIT` | Đang quay về source | receive-return |
| `COMPLETED`, `RETURNED`, `LOST`, `REJECTED`, `CANCELLED` | Kết thúc | không có action tiếp |

`DRAFT` có trong enum/DB nhưng API tạo hiện trả `PENDING`.

## 3. Tồn kho và số lượng

- Create/allocate/pick: **không** đổi on-hand.
- `approve-dispatch`: trừ source và tạo outbound receipt.
- Receive `GOOD`: cộng destination và tạo stock batch/inbound receipt.
- `QUARANTINE`, `DAMAGED`, `REJECTED`: ghi nhận quantity nhưng không tạo
  sellable stock; nhận đủ mà có non-GOOD thì `RECONCILING`.
- `receive-return`: cộng lại source; không tự cộng khi chỉ request return.

FE hiển thị các counter trong từng item:

```text
outstanding = max(0, requested - received - returned)
returnable  = max(0, shipped - receivedGood - returned)
```

Retry chỉ gửi `outstanding`; return chỉ gửi phần `returnable`. Không gửi lại
quantity đã nhận `GOOD`.

## 4. Quyền và staff hai đầu

- Tenant (`ROLE_TENANT`) duyệt xuất và xử lý reject/cancel, close-short, retry,
  return, reconcile; tenant vẫn có thể nhận hàng.
- Staff cần assignment `ACTIVE` đúng warehouse; staff được gán ở
  `sourceStaffId` là người được pick transfer đó.
- Staff được gán ở `destinationStaffId` được xem task và gọi `arrive`/`receive`
  tại kho đích hiện tại; staff khác bị chặn.
- Staff nguồn ở trạng thái `PENDING` có action `ALLOCATE`; sau đó mới `PICK`.
- `sourceStaffId` phải là staff active, thuộc tenant và được assign ở source.
- `destinationStaffId` phải được assign tại destination; khi retry sang kho mới
  thì assignment cũ bị bỏ và phải chọn staff của kho mới.
- FE nên load staff active của từng warehouse cho hai dropdown staff.
- Có thể lấy trực tiếp staff đang active tại một warehouse bằng
  `GET /api/tenant/staffs?warehouseId={id}&active=true`.
- Backend vẫn kiểm tra permission, tenant, contract, subscription và assignment;
  FE chỉ dùng để ẩn/hiện action.

## 5. API FE cần dùng

| Method | Endpoint | Body | Kết quả |
|---|---|---|---|
| `POST` | `/` | create request | `PENDING` |
| `GET` | `/`, `/{id}` | — | list/detail |
| `PATCH` | `/{id}/destination-staff` | `{ destinationStaffId, reason? }` | giao/reassign người nhận |
| `PATCH` | `/{id}/allocate` | — | `ALLOCATED` |
| `POST` | `/{id}/pick` | `{ lines: [{ sourceAllocationId, quantity }] }` | `PICKING`/`READY_TO_DISPATCH` |
| `PATCH` | `/{id}/approve-dispatch` | — | `IN_TRANSIT`, trừ source |
| `PATCH` | `/{id}/arrive` | — | `ARRIVED_AT_DESTINATION` |
| `POST` | `/{id}/receive` | receive request | partial/completed/reconciling |
| `PATCH` | `/{id}/reject-receipt` | `{ reason }` | `RECEIVE_REJECTED` |
| `PATCH` | `/{id}/close-short` | `{ reason }` | `SHORT_RECEIVED` |
| `POST` | `/{id}/retry` | `{ destinationWarehouseId, destinationStaffId?, expectedArrivalAt?, reason }` | `RETRY_REQUESTED` |
| `PATCH` | `/{id}/retry/dispatch` | — | retry `IN_TRANSIT` |
| `POST` | `/{id}/return/request` | `{ reason }` | `RETURN_REQUESTED` |
| `PATCH` | `/{id}/return/dispatch` | — | `RETURN_IN_TRANSIT` |
| `POST` | `/{id}/return/receive` | return request | `RETURNED`/`PARTIALLY_RETURNED` |
| `POST` | `/{id}/reconcile` | `{ resolution, reason? }` | completed/lost/return |
| `GET` | `/{id}/timeline` | — | event timeline |
| `PATCH` | `/{id}/reject`, `/{id}/cancel` | `{ reason }` | terminal trước dispatch |

### Create request

```json
{
  "sourceWarehouseId": "...",
  "destinationWarehouseId": "...",
  "sourceStaffId": "...",
  "destinationStaffId": "...",
  "expectedArrivalAt": "2026-09-12T10:00:00",
  "note": "...",
  "items": [{
    "skuId": "...",
    "requestedQuantity": 10,
    "sourceAllocations": [{
      "sourceStockBatchId": "...",
      "sourceRackId": "...",
      "sourceBinId": "...",
      "quantity": 10
    }]
  }]
}
```

Tổng source allocation của mỗi SKU phải bằng `requestedQuantity`; một SKU không
lặp trong cùng transfer.

### Receive request

```json
{
  "allowPartial": true,
  "destinationAllocations": [{
    "itemId": "...",
    "destinationRackId": "...",
    "destinationBinId": "...",
    "quantity": 6,
    "disposition": "GOOD"
  }]
}
```

Disposition: `GOOD`, `QUARANTINE`, `DAMAGED`, `REJECTED`.

- `allowPartial=false`: nhận đủ từng SKU trong một request (client cũ).
- `allowPartial=true`: nhận nhiều session; tổng lũy kế không vượt requested.
- Trong một request không lặp `(itemId, destinationRackId, destinationBinId)`.

### Reconcile/return request

`resolution`: `ACCEPT_AS_IS`, `DECLARE_LOST`, `RETURN_TO_SOURCE`.

```json
{
  "reason": "Hàng móp, cần trả source",
  "allowPartial": false,
  "lines": [{
    "itemId": "...",
    "quantity": 4,
    "sourceRackId": "...",
    "sourceBinId": "..."
  }]
}
```

Body trên là của `return/receive`; `reconcile` dùng `resolution`.

## 6. FE xử lý timeline/lỗi

- Sau mỗi mutation, lấy response backend làm state mới hoặc refetch detail; không
  tự cộng/trừ tồn ở FE.
- Hiển thị `destinationWarehouse` (tuyến gốc) và
  `currentDestinationWarehouse` (chặng hiện tại) riêng nhau.
- Hiển thị `attempts` theo `sequenceNo`: `OUTBOUND`, `FORWARD`, `RETURN` để thấy
  A → B → C → A.
- Timeline có thể có `ARRIVE` tự động khi gọi receive trực tiếp.
- Dùng `Idempotency-Key` ổn định cho một lần submit; timeout thì retry cùng key.
  Không dùng lại key cho payload khác. `reject`/`cancel` hiện không nhận header.
- `400`: body/quantity/layout/capacity sai; `403`: sai actor/assignment;
  `404`: không thấy resource; `409`: status/reservation đã đổi — refetch detail
  và timeline rồi render lại action.

## 7. Nguyên tắc cho Codex

1. Chỉ `StockTransferService` được mutation stock; FE không gọi receipt để giả lập.
2. Không đổi `destinationWarehouse` khi retry; tạo attempt mới và đổi current
   destination.
3. Chỉ dispatch/receive/receive-return được đổi on-hand.
4. Giữ transition guard, row lock, tenant scope và idempotency.
5. Thêm status mới phải sửa cả Java enum, DB check constraint, migration, API,
   service và test.
6. Mọi retry/return phải tính theo counter hiện có, không dispatch lại toàn bộ.
7. Chỉ destination staff đang được gán và còn assignment active mới được
   `arrive`/`receive`; tenant giữ quyền quyết định ngoại lệ.

File chính:

- Controller: `src/main/java/.../wms/transfer/controller/StockTransferController.java`
- Service: `src/main/java/.../wms/transfer/service/StockTransferService.java`
- Status: `src/main/java/.../wms/transfer/entity/StockTransferStatus.java`
- Attempt: `src/main/java/.../wms/transfer/entity/StockTransferAttempt.java`
- DTO: `src/main/java/.../wms/transfer/dto/`

## 8. Migration/deploy

Phải apply theo thứ tự:

```text
ops/migrations/20260909_01_stock_transfer_hardening.sql
ops/migrations/20260909_02_stock_transfer_operational_legs.sql
ops/migrations/20260910_01_stock_transfer_destination_staff.sql
```

```bash
bash ops/run-migrations.sh --dry-run
bash ops/run-migrations.sh
# hoặc: bash ops/run-migrations.sh --docker
```

Migration mở rộng status constraint, thêm cột/counter, tạo reservation,
pick-line, event, command, attempts và backfill attempt outbound cho dữ liệu cũ.
Backup DB trước khi chạy; không chỉ dựa vào Hibernate `ddl-auto=update`.

Kiểm tra sau khi sửa:

```bash
mvnw -q test
git diff --check
```
