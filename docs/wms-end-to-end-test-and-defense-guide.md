# StockSpace WMS — Hướng dẫn test end-to-end và thuyết trình nghiệp vụ

> Cập nhật theo source BE ngày 16/09/2026. Đây là tài liệu mô tả **hiện trạng code đang chạy**, không phải bản nghiệp vụ lý tưởng. Những điểm cần lưu ý hoặc còn lệch giữa FE và BE được ghi riêng ở cuối tài liệu.

## 1. Mục đích và phạm vi

Tài liệu này dùng cho ba việc:

1. Test thực tế từng bước trên web.
2. Biết chính xác mỗi bước thay đổi dữ liệu gì, tồn kho tăng/giảm lúc nào.
3. Giải thích trước hội đồng vì sao một luồng bị chặn khi Audit, vì sao Transfer cần Allocate/Pick/Dispatch/Receive, và cách các luồng tác động lẫn nhau.

Các luồng được bao phủ:

- Tenant mời Staff và gán Staff vào kho.
- Staff xem Warehouse Layout, Inventory và Tasks.
- Inbound.
- Put-away suggestion (gợi ý Rack/Bin).
- Outbound tự động FIFO và Outbound thủ công.
- Inventory Audit: blind count, sửa lại, kiểm lại, duyệt và điều chỉnh tồn.
- Transfer giữa hai kho: happy path, nhận thiếu, từ chối nhận, retry, return, reconciliation.
- Audit lock, transfer reservation, sức chứa Rack/Bin và tác động chéo giữa các luồng.

## 2. Cách nói ngắn gọn trước hội đồng

Có thể mở đầu bằng đoạn sau:

> StockSpace tách “chứng từ nghiệp vụ” khỏi “biến động tồn kho”. Việc tạo phiếu thường chỉ tạo một yêu cầu PENDING; tồn kho chỉ thay đổi tại điểm phê duyệt hoặc xác nhận vật lý phù hợp. Inbound tăng tồn khi Tenant duyệt. Outbound giảm tồn khi Tenant duyệt. Transfer giữ hàng khi Allocate, xác nhận lấy hàng khi Pick, giảm kho nguồn khi Dispatch và tăng kho đích khi Record Receipt. Audit khóa biến động tồn kho để ảnh chụp kiểm kê không bị lỗi thời; khi Tenant duyệt kết quả Audit, hệ thống mới sinh adjustment tăng hoặc giảm tồn.

Bốn nguyên tắc quan trọng nhất:

1. **Tạo phiếu không đồng nghĩa với thay đổi tồn kho.**
2. **Allocate chỉ giữ hàng; Dispatch mới trừ kho nguồn.**
3. **Receive chỉ nhập phần hàng GOOD vào kho đích.**
4. **Audit đang giữ lock thì những thao tác làm thay đổi tồn kho phải bị chặn.**

## 3. Thuật ngữ và công thức tồn kho

### 3.1. On-hand, Reserved và Available

- `onHand` / `quantity`: số lượng vật lý hệ thống đang ghi nhận trong `stock_batches`.
- `reservedQuantity`: số lượng đã được một Transfer ở trạng thái reservation ACTIVE giữ lại.
- `availableQuantity`: số có thể dùng cho nghiệp vụ khác.

```text
availableQuantity = max(0, onHand - reservedQuantity)
```

Ví dụ:

```text
Bin A có onHand = 10
Transfer TRF-01 đã Allocate = 6

reserved = 6
available = 10 - 6 = 4
```

Kết quả:

- Inventory vật lý vẫn hiển thị 10 nếu nhìn cột on-hand.
- Transfer TRF-01 đang giữ 6.
- Outbound hoặc Transfer khác chỉ được phép tiêu thụ tối đa 4.
- Khi TRF-01 bị Cancel trước Dispatch, reservation được thả và available trở lại 10.
- Khi TRF-01 Dispatch, on-hand giảm 6 và reservation 6 được consume; on-hand còn 4, reserved còn 0, available còn 4.

### 3.2. Các bộ đếm trong Transfer

- `requested`: số lượng Tenant yêu cầu chuyển.
- `reserved`: số lượng đã được Allocate/giữ ở kho nguồn.
- `picked`: số lượng Staff đã xác nhận lấy khỏi Rack/Bin.
- `shipped`: số lượng đã Dispatch và đã bị trừ khỏi kho nguồn.
- `received`: tổng số lượng kho đích đã ghi nhận vật lý, gồm GOOD và không GOOD.
- `receivedGood`: số GOOD đã tạo stock batch và được cộng tồn kho đích.
- `receivedDamaged`: tên field hiện tại dùng chung cho mọi disposition không GOOD, gồm `DAMAGED`, `QUARANTINE`, `REJECTED`.
- `returned`: số lượng đã nhận trả lại tại kho nguồn.
- `outstanding`: phần chưa nhận và chưa trả.

```text
outstanding = max(0, requested - received - returned)
returnable  = max(0, shipped - receivedGood - returned)
```

## 4. Vai trò, quyền và phạm vi kho

### 4.1. RBAC không phải là điều kiện duy nhất

Một request chỉ thành công khi qua đủ các lớp sau:

1. User đã đăng nhập.
2. Role có permission được controller yêu cầu.
3. Tenant có hợp đồng kho còn hiệu lực.
4. Tenant có subscription còn hiệu lực nếu thao tác là mutation WMS.
5. Nếu là Staff thì Staff phải có assignment ACTIVE tại kho liên quan.
6. Một số nghiệp vụ còn yêu cầu đúng người được gán trên phiếu.

Vì vậy có `INVENTORY_READ` chưa chắc đã xem được mọi kho. Staff còn phải được assign đúng kho.

### 4.2. Quyền mặc định liên quan WMS

| Chức năng | Tenant | Staff |
|---|---:|---:|
| Xem danh sách kho | Có | Có, chỉ kho được assign |
| Xem Layout | Có, endpoint Tenant | Có, endpoint Staff read-only |
| Xem Inventory | Có | Có, chỉ kho được assign; có thể bị mask khi blind count |
| Tạo Inbound/Outbound | Có | Có nếu được assign kho |
| Duyệt/Từ chối Inbound/Outbound | Có | Không, BE chặn Staff dù có `INVENTORY_UPDATE` |
| Tạo Audit | Có | Có nếu có `INVENTORY_AUDIT_MANAGE`; Staff tự gán cho chính mình |
| Đếm và nộp Audit | Có | Có nếu là Staff được gán phiếu |
| Duyệt Audit / yêu cầu kiểm lại | Có | Không |
| Tạo Transfer | Có | Có, nhưng Staff phải được assign cả kho nguồn và kho đích |
| Allocate/Pick Transfer | Có | Source Staff phù hợp |
| Arrive/Receive Transfer | Có | Đúng Destination Staff được gán |
| Retry/Return/Reconcile/Cancel Transfer | Có | Không |

### 4.3. Endpoint Layout đúng cho từng role

- Tenant: `GET /api/tenant/warehouses/{warehouseId}/layout`
- Staff: `GET /api/staff/warehouses/{warehouseId}/layout`

Nếu Staff gọi endpoint Tenant, RBAC có thể trả `403 FORBIDDEN` dù Staff đã được assign kho. FE Staff phải gọi endpoint `/api/staff/warehouses/.../layout`.

## 5. Staff lifecycle và màn hình Tasks

### 5.1. Mời Staff

API:

```http
POST /api/tenant/staffs/invite
```

Trình tự:

1. Kiểm tra Tenant có subscription ACTIVE.
2. Kiểm tra giới hạn số Staff theo gói.
3. Chuẩn hóa email về chữ thường.
4. Nếu cùng Tenant đã có invitation `PENDING` cho email đó: từ chối `STAFF_INVITATION_DUPLICATE`.
5. Nếu email đã là Tenant/Owner: từ chối.
6. Nếu email đã là member chưa bị xóa của Tenant: từ chối `STAFF_ALREADY_MEMBER`.
7. Tạo invitation hết hạn sau 48 giờ và gửi email.

### 5.2. Gán một Staff vào nhiều kho

API:

```http
POST /api/tenant/staffs/{staffUserId}/warehouses
```

Một Staff có thể có nhiều assignment ACTIVE trong các kho khác nhau của cùng Tenant. Nếu gán lại đúng kho đang ACTIVE, BE cập nhật `customTitle` và `notes`, không tạo assignment trùng.

### 5.3. Thu hồi assignment hoặc remove Staff

- Thu hồi một kho: assignment chuyển sang `REVOKED`, `isActive=false`.
- Remove Staff khỏi Tenant: `tenant_members` bị soft-delete, toàn bộ assignment ACTIVE của Tenant đó bị revoke.
- Dữ liệu lịch sử phiếu không bị xóa.
- Staff mất quyền đọc/thao tác kho ngay vì access service yêu cầu assignment ACTIVE.
- Có thể mời lại cùng email sau khi member cũ đã soft-delete; khi chấp nhận sẽ tạo membership mới.

Lưu ý đối với Transfer đang mở: remove/revoke Staff không tự hủy phiếu. Tenant phải gán lại Destination Staff hoặc tự xử lý các bước Tenant được phép làm.

### 5.4. Vì sao Staff mới tạo có thể thấy phiếu cũ trong Tasks?

`GET /api/staff/operations` là một **aggregate view**, không có bảng task riêng.

Nó tổng hợp động từ:

- `inventory_receipts` đang PENDING trong các kho Staff được assign.
- `inventory_audits` đang active.
- `stock_transfers` liên quan tới kho hoặc Staff.

Do đó Staff vừa được assign hôm nay vẫn có thể thấy receipt được tạo trước ngày Staff tham gia. `operationId` của dòng `RECEIPT` chính là `inventory_receipts.id`, không phải invitation ID và không phải assignment ID.

Đối với Receipt, `allowedActions` hiện chỉ là `VIEW`; Staff không được duyệt.

## 6. Inventory và Warehouse Layout

### 6.1. Inventory được tạo từ đâu?

Nguồn sự thật của tồn hiện tại là `stock_batches.quantity`. Mọi biến động có chứng từ tạo thêm `inventory_transactions`:

- Inbound APPROVED: transaction dương.
- Outbound APPROVED: transaction âm.
- Transfer Dispatch: transaction âm tại kho nguồn.
- Transfer Receive GOOD: transaction dương tại kho đích.
- Audit adjustment: transaction dương/âm theo chênh lệch.
- Return Receive: transaction dương tại kho nguồn.

### 6.2. Staff có xem được tồn kho không?

Có. Staff có `INVENTORY_READ` và assignment ACTIVE được xem tồn trong kho đó.

Ngoại lệ blind count:

- Khi chính Staff đang được gán một Audit ở `IN_PROGRESS`, `REOPENED` hoặc `RECOUNT_REQUIRED`, các batch/SKU nằm trong phạm vi Audit trả về `quantityMasked=true` và số lượng/reserved/available được che bằng 0.
- Các vị trí ngoài phạm vi Audit vẫn xem bình thường.
- Tenant không bị mask.
- Export inventory snapshot của Staff bị chặn trong các trạng thái trên để tránh lộ số hệ thống.

### 6.3. Layout có thay đổi tồn kho không?

Không. Layout mô tả Rack/Bin và giới hạn vật lý. Staff chỉ đọc; Tenant quản lý layout riêng của Tenant.

Hiện tại Audit lock được nối vào các đường thay đổi tồn kho, không được nối trực tiếp vào API sửa Layout. Khi demo nên tránh sửa/xóa Rack/Bin trong lúc có phiếu Audit hoặc Transfer đang mở, dù API layout có thể có validation riêng như không cho xóa vị trí còn hàng.

## 7. Inbound — nhập kho

### 7.1. Happy path

API tạo:

```http
POST /api/tenant/inventory/receipts
```

Payload mẫu:

```json
{
  "warehouseId": "<warehouseAId>",
  "type": "INBOUND",
  "senderName": "Nhà cung cấp Demo",
  "items": [
    {
      "skuId": "<kettleSkuId>",
      "quantity": 10,
      "rackId": "<rackA1Id>",
      "binId": "<binA1Id>",
      "note": "Lô demo inbound"
    }
  ]
}
```

| Bước | Trạng thái | Tồn kho | Dữ liệu thay đổi | Tác động luồng khác |
|---|---|---|---|---|
| Staff/Tenant tạo phiếu | `PENDING` | Chưa tăng | Tạo `inventory_receipts` và `inventory_receipt_items` | Outbound chưa dùng được số hàng này |
| Tenant duyệt | `APPROVED` | Tăng 10 | Tạo `stock_batches`, transaction `+10` | Inventory, Outbound và Transfer nhìn thấy hàng mới |
| Tenant từ chối | `REJECTED` | Không đổi | Lưu trạng thái và lý do | Không có batch mới |

Điều kiện:

- Rack bắt buộc.
- Bin bắt buộc.
- Bin phải thuộc Rack; Rack phải thuộc đúng layout/kho.
- Quantity tối thiểu 1.
- Capacity được kiểm tra lúc tạo và kiểm tra lại có row lock lúc duyệt.
- Staff có thể tạo, nhưng chỉ Tenant được duyệt/từ chối.

### 7.2. Inbound nhiều item

BE hỗ trợ `items` là danh sách nhiều phần tử. Mỗi item có SKU, quantity, Rack và Bin riêng. Toàn request chạy trong transaction; một dòng sai thì toàn bộ tạo/duyệt rollback.

### 7.3. Tác động của Audit

- Có thể tạo phiếu Inbound PENDING trong khi kho đang Audit.
- Không thể duyệt phiếu vì duyệt sẽ tăng tồn. BE trả `AUDIT_MOVEMENT_LOCKED`.
- Sau khi Audit APPROVED/CANCELLED hoặc lock được release, Tenant có thể duyệt lại phiếu PENDING.

## 8. Put-away suggestion — gợi ý vị trí nhập hàng

Endpoint suggestion là read-only. Nó không tự tạo phiếu và không giữ capacity.

Tiêu chí hiện tại theo thứ tự:

1. Chỉ xét Rack/Bin ACTIVE trong layout ACTIVE của Tenant.
2. Tính tải hiện tại của cả Rack và Bin theo weight và volume.
3. Loại vị trí không chứa thêm được ít nhất 1 đơn vị.
4. Ưu tiên Bin đã có cùng SKU để gom hàng.
5. Ưu tiên Bin chứa đủ toàn bộ phần quantity còn lại.
6. Best-fit: trong các vị trí phù hợp, ưu tiên vị trí có tỷ lệ capacity còn lại nhỏ nhất sau khi xếp, nhằm dùng vừa chỗ và giữ vị trí rộng cho hàng khác.
7. Nếu SKU nặng, ưu tiên vị trí thấp hơn.
8. Tie-break theo `rackCode`, `binCode`, sau đó `rackId`, `binId` để cùng một dữ liệu luôn sinh cùng một kết quả.

Ví dụ:

```text
Cần nhập 6 thùng SKU KETTLE.

Bin A: đã có cùng SKU, còn chứa 6, sau xếp còn 5%.
Bin B: chưa có SKU, còn chứa 6, sau xếp còn 2%.
Bin C: đã có cùng SKU, chỉ còn chứa 4.

Thứ tự ưu tiên:
- Tiêu chí “cùng SKU” đưa A và C lên trước B.
- Tiêu chí “chứa đủ phần còn lại” chọn A trước C.
- Kết quả: đề xuất 6 vào A.
```

Tie-break không có nghĩa là mã Rack/Bin tốt hơn về nghiệp vụ. Nó chỉ giải quyết trường hợp mọi tiêu chí trước đó bằng nhau, để kết quả không thay đổi ngẫu nhiên giữa hai lần gọi API.

## 9. Outbound — xuất kho

### 9.1. Outbound FIFO tự động

Khi request OUTBOUND không truyền Rack/Bin, BE tự lập pick list:

1. Lấy các batch ACTIVE đúng SKU và kho.
2. Sắp FIFO theo `arrivalDate` cũ nhất.
3. Nếu bằng `arrivalDate`, dùng `createdAt`.
4. Nếu vẫn bằng nhau, dùng `stockBatchId` làm tie-break.
5. Sau khi chọn batch, sắp lộ trình lấy hàng theo heuristic serpentine trên tọa độ layout.

Lộ trình serpentine:

- Sắp các hàng Rack theo tọa độ Y.
- Hàng đầu đi X tăng dần.
- Hàng sau đi X giảm dần.
- Tiếp tục xen kẽ như hình con rắn để giảm quay đầu.
- Trong Rack, sắp Bin theo X, tầng kệ, mã Bin và Bin ID.

Đây là heuristic đường đi, không phải thuật toán shortest path. Nó chỉ sắp thứ tự các vị trí đã được FIFO chọn; không đổi batch FIFO.

### 9.2. Outbound thủ công

Khi có ít nhất một item truyền Rack/Bin, request được xem là manual. Mọi item phải có cả Rack và Bin.

Một dòng request đại diện một `SKU + vị trí + quantity`. Muốn lấy cùng SKU từ hai Bin thì FE phải gửi hai dòng cùng SKU:

```json
{
  "warehouseId": "<warehouseAId>",
  "type": "OUTBOUND",
  "receiverName": "Khách hàng Demo",
  "items": [
    {
      "skuId": "<keyboardSkuId>",
      "quantity": 2,
      "rackId": "<rackA1Id>",
      "binId": "<binA1Id>"
    },
    {
      "skuId": "<keyboardSkuId>",
      "quantity": 1,
      "rackId": "<rackA2Id>",
      "binId": "<binA2Id>"
    }
  ]
}
```

BE lấy FIFO giữa các batch nằm trong từng Bin được chọn.

### 9.3. Vòng đời Outbound

| Bước | Trạng thái | Tồn kho | Ghi chú |
|---|---|---|---|
| Tạo phiếu | `PENDING` | Không đổi | Pick list chỉ là kế hoạch |
| Tenant duyệt | `APPROVED` | Trừ kho | Lock batch, validate lại, tạo transaction âm |
| Tenant từ chối | `REJECTED` | Không đổi | Phiếu kết thúc |

### 9.4. Hai phiếu Outbound giống nhau

Giả sử tồn kho là 3 và tạo hai phiếu đều yêu cầu 3:

1. Cả hai phiếu PENDING có thể được tạo vì tạo phiếu không reservation.
2. Duyệt phiếu thứ nhất: tồn từ 3 xuống 0.
3. Duyệt phiếu thứ hai: BE lock batch và phát hiện pick list cũ/không đủ hàng.
4. BE trả `409 OUTBOUND_PICK_LIST_STALE`.
5. FE có thể gọi replan; nếu vẫn thiếu thì không thể duyệt.

Việc FE gọi `approve` rồi tự gọi `replan` sau lỗi là hai API phục vụ hai mục đích khác nhau. Nếu cả hai trả 409, nghĩa là approve phát hiện stale và replan cũng không tìm đủ tồn mới. FE nên hiển thị một thông báo hợp nhất, không bắn hai toast đỏ gây hiểu nhầm.

### 9.5. Outbound và Transfer reservation

Nếu on-hand 10 và Transfer đã Allocate 6:

- Outbound 4 có thể duyệt.
- Outbound 5 phải bị chặn vì chỉ còn available 4.
- Phiếu Outbound PENDING vẫn có thể được tạo từ kế hoạch cũ; bước duyệt là điểm kiểm tra quyết định.

### 9.6. Tác động của Audit

- Tạo Outbound PENDING: được.
- Duyệt Outbound: bị chặn khi Audit lock đang active.
- Replan/read-only: không thay đổi tồn, nhưng kết quả có thể không hữu ích trong thời gian blind count.

## 10. Inventory Audit — kiểm kê

### 10.1. Vì sao Audit phải khóa movement?

Khi bắt đầu Audit, hệ thống chụp `expectedQuantity`. Nếu Inbound/Outbound/Transfer tiếp tục thay đổi tồn trong lúc Staff đang đếm, kết quả chênh lệch không còn có ý nghĩa.

BE hiện chọn chiến lược an toàn: **khóa toàn bộ warehouse**, kể cả Audit chỉ theo Rack hoặc Bin. Đây là quyết định bảo thủ của phiên bản hiện tại.

Khóa là khóa **biến động tồn kho**, không phải khóa toàn bộ thao tác đọc.

### 10.2. Phạm vi Audit

| Scope | Dữ liệu tạo phiếu | Dòng được snapshot | Thêm hàng phát sinh |
|---|---|---|---|
| `WAREHOUSE` | Chỉ warehouseId | Toàn kho | Bắt buộc chọn Rack và Bin |
| `RACK` | warehouseId + rackId | Mọi hàng trong Rack | Rack cố định, bắt buộc chọn Bin thuộc Rack |
| `BIN` | warehouseId + binId; rackId có thể bỏ | Mọi hàng trong Bin | Tự dùng đúng Rack/Bin của scope |

### 10.3. Ai được làm gì?

- Tenant có thể tạo phiếu và trực tiếp đếm/nộp. Khi `assignedTo` chính là người đó, BE không cho người thực hiện tự duyệt; nên luồng demo chuẩn vẫn tách Staff đếm và Tenant review.
- Staff có permission có thể tạo phiếu nhưng chỉ được gán cho chính mình.
- Staff chỉ đọc/đếm phiếu được assign cho mình.
- Tenant mới được `approve-edit`, `recount`, `approve` và `cancel`.
- Người Staff thực hiện kiểm kê không được tự duyệt phiếu của mình.

### 10.4. State machine và lock

| Trạng thái | Ý nghĩa | Movement lock | Số hệ thống trong audit detail đối với Staff | Sửa quantity |
|---|---|---:|---:|---:|
| `DRAFT` | Mới lập kế hoạch | Không | Chưa có snapshot | Không |
| `IN_PROGRESS` | Đã start, đang đếm | Có | Ẩn | Có |
| `SUBMITTED` | Đã nộp chờ Tenant | Có | Hiện | Không |
| `EDIT_REQUESTED` | Staff xin sửa | Có | Hiện | Không, chờ Tenant |
| `REOPENED` | Tenant cho sửa cùng vòng | Có | Hiện | Có |
| `RECOUNT_REQUIRED` | Tenant yêu cầu đếm vòng mới | Có | Ẩn | Chưa, phải Start lại |
| `APPROVED` | Đã điều chỉnh tồn | Không | Hiện | Không |
| `CANCELLED` | Đã hủy | Không | Theo lịch sử | Không |

Điểm dễ nhầm:

- `REOPENED` không phải blind count lại. Staff đã nộp và đã được xem số hệ thống, nên khi Tenant cho sửa thì số hệ thống vẫn hiện.
- `RECOUNT_REQUIRED` là một vòng đếm mới nên bị che lại; `countRound` tăng khi bấm Start lại.
- Khi chuyển sang `RECOUNT_REQUIRED`, movement lock vẫn được giữ vì audit chưa hoàn tất. Khi Staff Start lại, audit dùng lại lock của chính mình, tăng `countRound` và tạo snapshot mới. Audit khác không được Start cùng kho.

### 10.5. Test Audit từng bước

#### Bước A1 — Tạo phiếu

```http
POST /api/tenant/inventory/audits
```

Ví dụ Audit theo Bin:

```json
{
  "warehouseId": "<warehouseAId>",
  "scopeType": "BIN",
  "binId": "<binA1Id>",
  "assignedToId": "<staffId>",
  "note": "Kiểm kê demo Bin A1"
}
```

Kết quả:

- Status `DRAFT`.
- Chưa snapshot item.
- Chưa khóa kho.
- Các luồng khác vẫn hoạt động.

#### Bước A2 — Start

```http
POST /api/tenant/inventory/audits/{auditId}/start
```

Kết quả:

- Acquire `inventory_audit_locks` cho cả warehouse.
- Snapshot mỗi `SKU + Rack + Bin` thành một dòng `inventory_audit_items`.
- Nhiều stock batch cùng SKU và cùng vị trí được cộng lại thành một expected quantity.
- Status `IN_PROGRESS`.
- Staff thấy SKU/vị trí nhưng không thấy `expectedQuantity` và `discrepancy`.

Ngay lúc này:

- Inbound approve: block.
- Outbound approve: block.
- Transfer Allocate/Pick/Dispatch ở kho nguồn: block.
- Transfer Receive/Return Receive vào kho: block.
- Direct stock adjustment: block.
- Tạo các phiếu PENDING/read inventory: vẫn được, nhưng quantity trong scope bị mask với Staff được assign Audit.

#### Bước A3 — Nhập số đếm

```http
PUT /api/tenant/inventory/audits/{auditId}/counts
```

Payload:

```json
{
  "items": [
    {
      "itemId": "<auditItemId>",
      "actualQuantity": 5,
      "note": "Đếm trực tiếp tại Bin",
      "varianceReason": "Tìm thấy thêm 2 thùng"
    }
  ]
}
```

`actualQuantity` là **tổng số thực tế cuối cùng**, không phải số cộng thêm. Không được âm.

#### Bước A4 — Thêm hàng phát sinh

Hàng phát sinh là SKU có mặt vật lý nhưng không có dòng snapshot tại vị trí đó.

```http
POST /api/tenant/inventory/audits/{auditId}/unexpected-items
```

```json
{
  "skuId": "<mouseSkuId>",
  "actualQuantity": 1,
  "rackId": "<rackA1Id>",
  "binId": "<binA1Id>",
  "note": "Tìm thấy kiện có nhãn MOUSE-0910-02"
}
```

BE lưu:

```text
itemOrigin = UNEXPECTED
expectedQuantity = 0
actualQuantity = 1
discrepancy = +1
```

Dòng snapshot bình thường có `itemOrigin=SNAPSHOT`.

Quy tắc trùng:

- Cùng SKU + Rack + Bin chỉ được có một dòng trong cùng round.
- Nếu bấm thêm lần nữa cùng vị trí, BE trả `AUDIT_ITEM_DUPLICATE`.
- Staff phải sửa `actualQuantity` của dòng đã có thành tổng cuối cùng; không tự cộng âm thầm.
- Cùng SKU ở hai Bin khác nhau là hai dòng khác nhau.

Về nhận diện thực tế: Staff phải dựa vào nhãn SKU, barcode/QR, mã kiện hoặc mở/kiểm tra theo quy trình kho. Hệ thống không thể tự biết thùng là Chuột hay Ấm nếu kiện không có định danh.

#### Bước A5 — Lưu tiến độ và Submit

```http
POST /api/tenant/inventory/audits/{auditId}/submit
```

BE chỉ cho Submit khi mọi dòng current round đã `COUNTED` và có actual quantity.

Kết quả:

- Status `SUBMITTED`.
- Lock vẫn giữ.
- Staff được thấy expected quantity và discrepancy trong chi tiết phiếu.
- Quantity không còn sửa trực tiếp.

#### Bước A6a — Tenant duyệt

```http
POST /api/tenant/inventory/audits/{auditId}/approve
```

Với từng dòng:

```text
delta = actualQuantity - expectedQuantity
```

- `delta = 0`: không sinh adjustment.
- `delta > 0`: tạo batch mới đúng Rack/Bin của dòng, tạo receipt INBOUND APPROVED và transaction dương.
- `delta < 0`: trừ FIFO từ các batch cùng SKU + Rack + Bin, tạo receipt OUTBOUND APPROVED và transaction âm.

Sau khi toàn bộ thành công:

- Status `APPROVED`.
- Release movement lock.
- Các luồng đang bị block có thể chạy lại.

Toàn bộ approval là transaction. Nếu một dòng lỗi capacity hoặc stock fingerprint thay đổi, toàn bộ duyệt rollback; không có chuyện duyệt nửa phiếu.

#### Bước A6b — Staff xin sửa, Tenant cho sửa

1. Staff từ `SUBMITTED` gọi `request-edit` kèm lý do.
2. Status `EDIT_REQUESTED`; lock vẫn giữ; chưa sửa quantity được.
3. Tenant gọi `approve-edit`.
4. Status `REOPENED`; Staff sửa quantity và Submit lại.

Trong `REOPENED`, Staff thấy số hệ thống trong chi tiết phiếu. Đây là chủ ý: Staff đã hoàn tất blind count lần đầu và đang sửa trên kết quả đã được reveal.

#### Bước A6c — Tenant yêu cầu kiểm lại

1. Tenant từ `SUBMITTED` gọi `recount` kèm lý do.
2. Status `RECOUNT_REQUIRED`.
3. Movement lock vẫn được giữ để bảo toàn kết quả kiểm kê chưa được xử lý.
4. Staff bấm Start lại.
5. `countRound` tăng, lock hiện tại được dùng lại và snapshot round mới được tạo.
6. Staff đếm lại theo blind count.

Trong lúc `RECOUNT_REQUIRED`, không được Start một Audit khác cùng warehouse. Đây là cách ngăn lỗi đã từng xảy ra: một phiếu chờ kiểm lại nhưng phiếu mới vẫn bắt đầu được.

#### Bước A6d — Cancel

Tenant có thể Cancel khi chưa APPROVED/CANCELLED. Cancel release lock và không điều chỉnh tồn.

### 10.6. Chênh lệch và capacity

Ví dụ Bin có expected 3, Staff nhập actual 5:

```text
delta = +2
```

Khi Tenant duyệt, hệ thống thử inbound +2 đúng Bin đó. Nếu +2 làm Rack hoặc Bin vượt max weight/max volume, approval thất bại và rollback. Hệ thống **không** đánh dấu `OVER_CAPACITY` rồi vẫn nhét hàng vào.

Giải thích thực tế:

- Nếu hàng thật đang nằm trong Bin nhưng cấu hình nói vượt tải, ít nhất một dữ liệu đang không khớp thực tế: cấu hình kích thước/tải, unit weight/volume, số đếm, hoặc hàng đã được đặt sai quy trình.
- Không nên tự hợp thức hóa bằng cách cộng tồn vượt capacity.
- Cần kiểm tra lại hoặc di chuyển vật lý sang Bin khác qua một nghiệp vụ movement phù hợp rồi kiểm kê lại.

### 10.7. Audit không dùng để chuyển vị trí

Audit trả lời câu hỏi: “Tại vị trí đang kiểm, số thực tế là bao nhiêu?”. Nó không phải lệnh chuyển hàng.

Nếu muốn chuyển 2 thùng từ Bin A sang Bin B:

1. Hoàn tất/hủy Audit để release lock.
2. Thực hiện nghiệp vụ movement/transfer vị trí phù hợp.
3. Nếu cần, tạo Audit mới để xác nhận sau di chuyển.

Không nên nhập Bin A thiếu 2 và tự tạo Bin B dư 2 trong cùng Audit như một cách thay thế movement, vì lịch sử sẽ bị hiểu là chênh lệch kiểm kê chứ không phải chuyển vị trí có chủ đích.

## 11. Transfer giữa hai kho

### 11.1. Bản chất của Transfer

Transfer là một chuỗi có kiểm soát:

```text
REQUEST -> ALLOCATE -> PICK -> DISPATCH -> ARRIVE -> RECEIVE
```

- Request: chọn cần chuyển gì.
- Allocate: giữ hàng trên hệ thống.
- Pick: xác nhận nhân viên đã lấy hàng vật lý khỏi vị trí nguồn.
- Dispatch: xe rời kho; lúc này mới trừ tồn nguồn.
- Arrive: xe đến kho đích; chưa cộng tồn.
- Receive: kiểm nhận, phân vào Rack/Bin đích; phần GOOD mới cộng tồn đích.

### 11.2. Tạo Transfer

```http
POST /api/tenant/inventory/transfers
```

Payload mẫu:

```json
{
  "sourceWarehouseId": "<warehouseAId>",
  "destinationWarehouseId": "<warehouseBId>",
  "sourceStaffId": "<sourceStaffId>",
  "destinationStaffId": "<destinationStaffId>",
  "expectedArrivalAt": "2026-09-18T10:00:00",
  "note": "Chuyển 6 ấm siêu tốc",
  "items": [
    {
      "skuId": "<kettleSkuId>",
      "requestedQuantity": 6,
      "sourceAllocations": [
        {
          "sourceStockBatchId": "<batchBinA1Id>",
          "sourceRackId": "<rackA1Id>",
          "sourceBinId": "<binA1Id>",
          "quantity": 2
        },
        {
          "sourceStockBatchId": "<batchBinA2Id>",
          "sourceRackId": "<rackA2Id>",
          "sourceBinId": "<binA2Id>",
          "quantity": 4
        }
      ]
    }
  ]
}
```

Validation chính:

- Kho nguồn khác kho đích.
- Cả hai kho active, cùng phạm vi Tenant và có hợp đồng WMS.
- Nếu creator là Staff, Staff phải được assign cả hai kho.
- Source/Destination Staff nếu truyền phải là Staff ACTIVE đúng Tenant và đúng assignment kho.
- Mỗi SKU chỉ xuất hiện một lần trong `items`.
- Mỗi source batch chỉ xuất hiện một lần cho SKU.
- Batch phải đúng SKU, kho, Rack và Bin.
- Tổng source allocations phải bằng requested quantity.
- `expectedArrivalAt` phải ở tương lai; bỏ trống thì mặc định +48 giờ.

Sau Create:

- Status `PENDING`.
- Tạo source allocation plan và OUTBOUND attempt `PLANNED`.
- Không reservation.
- Không giảm tồn.
- Vì chưa reservation, một Transfer khác vẫn có thể lập kế hoạch trên cùng batch.

### 11.3. Allocate stock

```http
PATCH /api/tenant/inventory/transfers/{transferId}/allocate
```

Allocate nghĩa là **giữ hàng**, không phải xuất kho.

BE:

1. Chỉ nhận từ `PENDING`.
2. Kiểm tra source warehouse không bị Audit lock.
3. Lock các batch nguồn.
4. Tính tồn khả dụng sau reservation của transfer khác.
5. Tạo `stock_transfer_reservations` status ACTIVE.
6. Tăng `reservedQuantity` trên transfer item.
7. Chuyển status `ALLOCATED`.

Không thay đổi `stock_batches.quantity`.

Nếu TRF-01 Allocate trước, TRF-02 cùng dùng số đó có thể đã Create thành công nhưng Allocate sẽ lỗi `STOCK_TRANSFER_RESERVATION_CONFLICT`.

Nút `Allocate stock` xuất hiện khi đang xem phiếu từ context kho nguồn hay kho đích vẫn trỏ cùng một hành động của phiếu. Về nghiệp vụ nó luôn allocate tại **source warehouse**. FE nên ghi rõ `Allocate source stock` để tránh hiểu là allocate kho đang chọn trên dropdown.

### 11.4. Confirm picking

```http
POST /api/tenant/inventory/transfers/{transferId}/pick
```

Payload:

```json
{
  "lines": [
    {
      "sourceAllocationId": "<sourceAllocationId>",
      "quantity": 2
    }
  ]
}
```

Confirm picking nghĩa là Source Staff xác nhận đã lấy vật lý hàng theo allocation.

- Cho phép ở `ALLOCATED` hoặc `PICKING`.
- Có thể pick nhiều đợt.
- Cumulative picked không được vượt allocation.
- Pick một phần: status `PICKING`.
- Pick đủ toàn bộ item: `READY_TO_DISPATCH`.
- Chưa giảm tồn kho; reservation vẫn giữ.
- Tạo lịch sử `stock_transfer_pick_lines`.

### 11.5. Approve Dispatch

```http
PATCH /api/tenant/inventory/transfers/{transferId}/approve-dispatch
```

Đây là điểm hàng rời kho nguồn:

1. Tenant duyệt Dispatch.
2. Kiểm tra Audit lock nguồn.
3. Lock/revalidate batch và reservation.
4. Tạo OUTBOUND receipt APPROVED nội bộ.
5. Tạo transaction âm.
6. Trừ `stock_batches.quantity` nguồn.
7. Consume/release reservation tương ứng.
8. Cập nhật `shippedQuantity`.
9. Status `IN_TRANSIT` và attempt `IN_TRANSIT`.

Luồng FE chuẩn phải đi `ALLOCATED -> PICKING/READY_TO_DISPATCH -> IN_TRANSIT`.

BE còn hỗ trợ approve-dispatch trực tiếp từ `PENDING` hoặc `ALLOCATED` để tương thích client cũ. Không nên dùng nhánh legacy khi demo vì bỏ qua ý nghĩa vận hành của bước picking.

### 11.6. Arrive at destination

```http
PATCH /api/tenant/inventory/transfers/{transferId}/arrive
```

- `IN_TRANSIT`/`OVERDUE` -> `ARRIVED_AT_DESTINATION`.
- Không cộng tồn kho đích.
- Chỉ ghi nhận kiện/xe đã đến.
- Nếu FE gọi Receive ngay mà chưa Arrive, BE có thể tự ghi event Arrive rồi chuyển qua Receiving.

### 11.7. Record receipt

```http
POST /api/tenant/inventory/transfers/{transferId}/receive
```

Payload mẫu:

```json
{
  "allowPartial": true,
  "destinationAllocations": [
    {
      "itemId": "<transferItemId>",
      "destinationRackId": "<rackB1Id>",
      "destinationBinId": "<binB1Id>",
      "quantity": 6,
      "disposition": "GOOD"
    }
  ]
}
```

Record receipt nghĩa là kiểm nhận tại kho đích và ghi số lượng/vị trí/disposition.

Validation:

- Đúng Destination Staff được assign hoặc Tenant.
- Đúng layout active của current destination.
- Rack/Bin thuộc kho đích và Bin thuộc Rack.
- Quantity dương.
- Không trùng cùng item/location trong một request.
- Cumulative receive không vượt requested.
- Rack/Bin không vượt weight/volume.
- Destination warehouse không bị Audit lock.

Disposition:

| Disposition | Tính vào received | Tạo stock batch đích | Cộng tồn đích | Hướng xử lý |
|---|---:|---:|---:|---|
| `GOOD` | Có | Có | Có | Hàng dùng được |
| `QUARANTINE` | Có | Không | Không | Chờ kiểm tra |
| `DAMAGED` | Có | Không | Không | Hàng hư |
| `REJECTED` | Có | Không | Không | Không chấp nhận |

Trạng thái sau Receive:

- Chưa nhận đủ: `PARTIALLY_RECEIVED`.
- Đã nhận đủ vật lý và tất cả GOOD: `COMPLETED`.
- Đã nhận đủ vật lý nhưng có non-GOOD: `RECONCILING`.

### 11.8. Nhận một phần và nhận thiếu

Với `allowPartial=true`, có thể Record receipt nhiều lần. Phần GOOD mỗi lần được nhập kho ngay.

Nếu quyết định không chờ phần còn lại:

```http
PATCH /api/tenant/inventory/transfers/{transferId}/close-short
```

- Chỉ Tenant.
- Từ `PARTIALLY_RECEIVED`.
- Chuyển `SHORT_RECEIVED`.
- Không tự tăng/giảm tồn ở bước close-short.
- Sau đó Tenant chọn Retry phần thiếu hoặc Return phần có thể thu hồi.

### 11.9. Từ chối nhận toàn bộ

```http
PATCH /api/tenant/inventory/transfers/{transferId}/reject-receipt
```

- Chỉ Tenant.
- Chỉ khi chưa nhận bất kỳ quantity nào.
- Status `RECEIVE_REJECTED`.
- Không tạo tồn đích.
- Nếu đã nhận một phần, không dùng reject-receipt; phải close-short hoặc xử lý reconcile phù hợp.

### 11.10. Retry sang kho đích khác

Áp dụng từ `RECEIVE_REJECTED` hoặc `SHORT_RECEIVED`.

1. Tenant chọn destination warehouse mới và optional Destination Staff.
2. Status `RETRY_REQUESTED`.
3. Tạo FORWARD attempt cho `outstanding`.
4. `dispatchRetry` -> `IN_TRANSIT`.
5. Kho đích mới Arrive/Receive như bình thường.

Retry Dispatch không trừ kho nguồn lần nữa vì hàng đã bị trừ từ Dispatch đầu tiên.

Response giữ hai khái niệm:

- `destinationWarehouse`: kho đích ban đầu, phục vụ lịch sử.
- `currentDestinationWarehouse`: kho đích đang active sau retry.

### 11.11. Return to source

Return không tự cộng lại kho nguồn ngay khi bấm yêu cầu.

1. `requestReturn` -> `RETURN_REQUESTED`.
2. `dispatchReturn` -> `RETURN_IN_TRANSIT`.
3. `receiveReturn` chọn Rack/Bin kho nguồn.
4. Khi nhận trả, tạo INBOUND receipt APPROVED, stock batch mới và transaction dương tại kho nguồn.
5. Nhận đủ -> `RETURNED`; nhận một phần -> `PARTIALLY_RETURNED`.

Return Receive cũng kiểm tra Audit lock và capacity của kho nguồn.

### 11.12. Reconciliation và ba lựa chọn

Ví dụ yêu cầu chuyển 6:

```text
Kho đích ghi nhận 5 GOOD + 1 DAMAGED.
received = 6
receivedGood = 5
non-GOOD = 1
Tồn kho đích chỉ tăng 5
Status = RECONCILING
```

Tenant chọn một trong ba:

#### `ACCEPT_AS_IS`

- Chấp nhận hiện trạng đã ghi nhận.
- Status `COMPLETED`.
- Không tạo thêm stock.
- 5 GOOD vẫn là tồn dùng được; 1 non-GOOD không tự biến thành hàng tốt.
- Dùng khi tổ chức chấp nhận kết quả, xử lý hư hỏng bằng quy trình ngoài phạm vi transfer.

#### `DECLARE_LOST`

- Ghi nhận phần không GOOD/mất là tổn thất.
- Status `LOST`.
- Không cộng thêm kho đích và không trả về nguồn.
- Dùng khi không còn hàng vật lý để thu hồi.

#### `RETURN_TO_SOURCE`

- Status `RETURN_REQUESTED`.
- Tạo RETURN attempt cho phần `returnable`.
- Chưa cộng tồn nguồn tại thời điểm chọn.
- Chỉ khi `receiveReturn` tại kho nguồn thì phần trả mới tạo batch và transaction dương.

Lưu ý hiện trạng code: Return Receive tạo stock batch bình thường tại kho nguồn. Nếu hàng trả là DAMAGED/QUARANTINE, quy trình thực tế nên có khu/bin cách ly và FE nên buộc chọn vị trí cách ly phù hợp.

### 11.13. Cancel và Reject Transfer

- Reject: chỉ từ `PENDING`, status `REJECTED`.
- Cancel: cho phép trước Dispatch ở `PENDING`, `ALLOCATED`, `PICKING`, `READY_TO_DISPATCH`.
- Nếu đã Allocate, Cancel release reservation.
- Không đổi on-hand vì hàng chưa Dispatch.
- Sau `IN_TRANSIT` không dùng Cancel; phải theo nhánh receive/reject/retry/return/reconcile.

### 11.14. Overdue

- Mặc định SLA là 48 giờ nếu không nhập `expectedArrivalAt`.
- Scheduler có thể chuyển shipment quá hạn ở `IN_TRANSIT`, `ARRIVED_AT_DESTINATION` hoặc `PARTIALLY_RECEIVED` sang `OVERDUE`.
- Overdue chỉ là cảnh báo trạng thái; không tự thay đổi tồn kho.
- Vẫn có thể Arrive/Receive theo điều kiện quyền.

### 11.15. Idempotency

Nhiều endpoint mutation của Transfer nhận header:

```http
Idempotency-Key: <uuid-cho-mot-thao-tac-logic>
```

Quy tắc:

- Client timeout rồi retry đúng cùng request: dùng lại key cũ.
- Cùng key + cùng payload: BE trả kết quả command trước, không thực hiện hai lần.
- Cùng key + payload khác: conflict.
- Mỗi hành động logic mới phải dùng key mới.

Create Transfer, Reject và Cancel hiện không đồng đều hỗ trợ header này; FE không nên giả định mọi endpoint đều idempotent như nhau.

## 12. Ma trận tác động chéo của Audit lock

Audit lock hiện là warehouse-wide.

| Thao tác tại kho đang lock | Được hay bị chặn | Lý do |
|---|---|---|
| Xem Layout | Được | Read-only |
| Xem Inventory Tenant | Được | Read-only |
| Xem Inventory Staff đang audit | Được nhưng quantity trong scope bị mask | Blind count |
| Export inventory snapshot của Staff đang audit | Bị chặn | Tránh lộ số hệ thống |
| Tạo Inbound PENDING | Được | Chưa tăng tồn |
| Duyệt Inbound | Bị chặn | Tăng tồn |
| Tạo Outbound PENDING | Được | Chưa giảm tồn |
| Duyệt Outbound | Bị chặn | Giảm tồn |
| Tạo Transfer | Được | Chỉ lập kế hoạch |
| Allocate Transfer nếu kho là nguồn | Bị chặn | Giữ hàng dựa trên snapshot đang kiểm |
| Pick Transfer nếu kho là nguồn | Bị chặn | Ghi nhận lấy hàng vật lý |
| Dispatch Transfer nếu kho là nguồn | Bị chặn | Giảm tồn |
| Arrive Transfer nếu kho là đích | Được | Chỉ đổi trạng thái, chưa tăng tồn |
| Record Receipt Transfer nếu kho là đích | Bị chặn | Tăng tồn phần GOOD |
| Retry/Return request | Thường được | Chỉ quyết định workflow |
| Retry/Return dispatch | Được nếu không mutation stock tại kho lock | Chỉ đổi trạng thái vận chuyển |
| Return Receive nếu kho nguồn đang lock | Bị chặn | Tăng lại tồn nguồn |
| Direct stock adjustment | Bị chặn | Thay đổi tồn |
| Audit approve của chính lock owner | Được | Adjustment thuộc transaction của Audit đó |

## 13. Ma trận reservation và cạnh tranh tồn kho

| Tình huống | Kết quả hiện tại |
|---|---|
| Hai Transfer cùng Create trên một batch | Cả hai có thể PENDING |
| Transfer 1 Allocate trước | Tạo reservation ACTIVE |
| Transfer 2 Allocate phần đã bị giữ | `STOCK_TRANSFER_RESERVATION_CONFLICT` |
| Outbound chỉ dùng phần available | Duyệt được |
| Outbound cố dùng phần Transfer đã giữ | `OUTBOUND_PICK_LIST_STALE` khi duyệt |
| Direct adjustment âm ăn vào reserved | Bị chặn reservation conflict |
| Inbound dương | Không bị reservation chặn; vẫn kiểm capacity |
| Transfer Cancel trước Dispatch | Reservation được release |
| Transfer Dispatch | Reservation được consume và on-hand giảm |

## 14. Capacity — weight và volume

Capacity được kiểm ở cả Rack và Bin.

```text
projectedWeight = currentWeight + sum(unitWeight * incomingQuantity)
projectedVolume = currentVolume + sum(unitVolume * incomingQuantity)
```

Nếu max được cấu hình và projected vượt max:

- Inbound create/approve có thể bị chặn.
- Transfer receive bị rollback.
- Return receive bị rollback.
- Audit approve với delta dương bị rollback.
- Suggestion không đề xuất phần vượt sức chứa; nếu tổng capacity thiếu, trả `unallocatedQuantity`.

Một request nhiều dòng được tính gộp. Không thể lách capacity bằng cách đưa hai item cùng vào một Bin trong cùng request.

## 15. Dữ liệu demo đề xuất

Chuẩn bị hai kho cùng Tenant:

```text
Kho A: Kho Nguồn Demo
  Rack A1
    Bin A1-01: KETTLE = 4
    Bin A1-02: KEYBOARD = 2
  Rack A2
    Bin A2-01: KETTLE = 5
    Bin A2-02: KEYBOARD = 1

Kho B: Kho Đích Demo
  Rack B1
    Bin B1-01: trống, đủ capacity
    Bin B1-02: trống, capacity nhỏ

Staff nguồn: source.staff.demo@example.com, assign Kho A
Staff đích: destination.staff.demo@example.com, assign Kho B

SKU:
KETTLE-DEMO-01, unitWeight = 2 kg, unitVolume = 0.02 m3
KEYBOARD-DEMO-01, unitWeight = 1 kg, unitVolume = 0.01 m3
MOUSE-DEMO-01, unitWeight = 0.5 kg, unitVolume = 0.005 m3
```

Lưu lại ID từ API/Network:

```text
warehouseAId=
warehouseBId=
rackA1Id=
binA1_01Id=
rackB1Id=
binB1_01Id=
kettleSkuId=
sourceStaffId=
destinationStaffId=
```

## 16. Bộ test bắt buộc trước khi demo

### TC-STAFF-01 — Một Staff nhiều kho

1. Mời Staff và accept.
2. Assign Kho A.
3. Assign Kho B.
4. Đăng nhập Staff, kiểm tra `my-warehouses` có cả hai.
5. Revoke Kho A, xác nhận Staff chỉ còn Kho B và API Kho A trả Forbidden.

### TC-IN-01 — Inbound nhiều item

1. Staff tạo phiếu 2 SKU vào hai Bin.
2. Xác nhận Inventory chưa tăng.
3. Tenant duyệt.
4. Xác nhận cả hai batch tăng và có transaction dương.

### TC-OUT-01 — Hai phiếu Outbound cạnh tranh

1. Chuẩn bị tồn KEYBOARD = 3.
2. Tạo hai phiếu, mỗi phiếu xuất 3.
3. Duyệt phiếu 1; tồn thành 0.
4. Duyệt phiếu 2; kỳ vọng 409 stale.
5. Replan phiếu 2; kỳ vọng shortage/không đủ.

### TC-AUD-01 — Happy path chênh lệch

1. Tạo Audit Bin có expected KETTLE = 3.
2. Start; kiểm tra Staff không thấy expected.
3. Nhập actual = 5.
4. Submit; kiểm tra Staff thấy expected 3 và discrepancy +2.
5. Tenant approve.
6. Xác nhận tồn Bin thành 5, có adjustment INBOUND +2 và lock được release.

### TC-AUD-02 — Hàng phát sinh

1. Trong Audit, thêm MOUSE actual = 1 đúng Rack/Bin.
2. Kiểm tra `itemOrigin=UNEXPECTED`, expected=0.
3. Thêm lại MOUSE cùng vị trí; kỳ vọng `AUDIT_ITEM_DUPLICATE`.
4. Submit và approve.
5. Xác nhận batch MOUSE +1 đúng Bin.

### TC-AUD-03 — Audit block movement

1. Start Audit Kho A.
2. Tạo Inbound PENDING: được.
3. Duyệt Inbound: bị `AUDIT_MOVEMENT_LOCKED`.
4. Tạo Outbound PENDING: được.
5. Duyệt Outbound: bị lock.
6. Tạo Transfer Kho A -> Kho B: được.
7. Allocate Transfer: bị lock.
8. Cancel Audit.
9. Thử lại các bước duyệt/allocate: được nếu dữ liệu khác hợp lệ.

### TC-AUD-04 — Edit sau Submit

1. Staff Submit.
2. Staff request-edit.
3. Thử sửa ngay ở `EDIT_REQUESTED`: bị chặn.
4. Tenant approve-edit.
5. Status `REOPENED`, expected vẫn hiện trong audit detail.
6. Staff sửa actual và Submit lại.

### TC-AUD-05 — Recount loại trừ Audit khác

1. Staff Submit Audit 1.
2. Tenant request recount.
3. Audit 1 thành `RECOUNT_REQUIRED`, movement lock vẫn active.
4. Tạo Audit 2 cùng kho rồi Start.
5. Kỳ vọng Audit 2 bị `AUDIT_MOVEMENT_LOCKED`.
6. Start lại Audit 1, lock được dùng lại và `countRound` tăng.

### TC-TRF-01 — Happy path Transfer

1. Create 6 KETTLE từ Kho A sang Kho B.
2. Xác nhận PENDING và on-hand nguồn chưa đổi.
3. Allocate; reserved=6, available giảm 6, on-hand chưa đổi.
4. Pick 2; status PICKING.
5. Pick 4; status READY_TO_DISPATCH.
6. Tenant Dispatch; on-hand nguồn giảm 6, status IN_TRANSIT.
7. Destination Staff Arrive; tồn đích chưa tăng.
8. Receive 6 GOOD vào Bin B1-01; tồn đích tăng 6, status COMPLETED.

### TC-TRF-02 — Reservation cạnh tranh

1. Tạo TRF-01 và TRF-02 dùng cùng 6 hàng.
2. Allocate TRF-01: thành công.
3. Allocate TRF-02: reservation conflict.
4. Cancel TRF-01.
5. Allocate lại TRF-02: thành công.

### TC-TRF-03 — Partial receive và close short

1. Transfer 6, Dispatch đủ 6.
2. Receive 4 GOOD với allowPartial=true.
3. Status PARTIALLY_RECEIVED, tồn đích +4.
4. Tenant close-short.
5. Status SHORT_RECEIVED, 2 còn outstanding.
6. Chọn retry 2 sang kho khác hoặc return phần returnable theo nghiệp vụ.

### TC-TRF-04 — Damaged và Reconcile

1. Transfer 6.
2. Receive 5 GOOD + 1 DAMAGED.
3. Xác nhận tồn đích chỉ +5 và status RECONCILING.
4. Chạy ba phiếu riêng để test từng resolution:
   - ACCEPT_AS_IS -> COMPLETED.
   - DECLARE_LOST -> LOST.
   - RETURN_TO_SOURCE -> RETURN_REQUESTED -> dispatch return -> receive return.

### TC-TRF-05 — Audit tại nguồn và đích

1. Start Audit Kho A; Transfer A -> B Create được nhưng Allocate/Pick/Dispatch bị chặn.
2. Kết thúc Audit A; Dispatch Transfer.
3. Start Audit Kho B trước Receive.
4. Arrive được nhưng Record receipt bị chặn.
5. Kết thúc Audit B; Receive thành công.

### TC-CAP-01 — Vượt capacity

1. Cấu hình Bin B chỉ còn capacity cho 2 KETTLE.
2. Transfer Receive 3 GOOD vào Bin B.
3. Kỳ vọng toàn request lỗi, tồn đích không tăng phần nào.
4. Chia allocation sang hai Bin hợp lệ rồi Receive lại.

## 17. Các bảng dữ liệu chính để giải thích kỹ thuật

### Inventory/Receipt

- `inventory_receipts`: header phiếu Inbound/Outbound, gồm cả receipt nội bộ của Transfer/Audit.
- `inventory_receipt_items`: từng dòng SKU/vị trí/quantity.
- `stock_batches`: tồn theo SKU + kho + Rack + Bin + lô.
- `inventory_transactions`: sổ biến động dương/âm liên kết receipt và batch.

### Audit

- `inventory_audits`: phiếu, scope, assignee, round, status.
- `inventory_audit_items`: snapshot/actual/discrepancy và `item_origin`.
- `inventory_audit_locks`: lock warehouse active/released.
- `inventory_audit_adjustments`: liên kết audit item với receipt/batch điều chỉnh.

### Transfer

- `stock_transfers`: header và trạng thái tổng.
- `stock_transfer_items`: requested/reserved/picked/shipped/received/returned theo SKU.
- `stock_transfer_source_allocations`: kế hoạch lấy batch/Rack/Bin nguồn.
- `stock_transfer_reservations`: phần hàng được giữ.
- `stock_transfer_pick_lines`: lịch sử xác nhận pick.
- `stock_transfer_destination_allocations`: vị trí/disposition đã nhận.
- `stock_transfer_attempts`: chặng OUTBOUND/FORWARD/RETURN.
- `stock_transfer_events`: timeline nghiệp vụ.
- `stock_transfer_commands`: idempotency command history.

## 18. Những điểm hiện trạng cần nói cẩn thận

1. **Tasks không phải bảng assignment riêng.** Receipt PENDING trong kho được assign sẽ xuất hiện cho Staff, kể cả tạo trước ngày Staff tham gia.
2. **Audit lock hiện khóa toàn kho.** Scope Rack/Bin chỉ giới hạn item được đếm và mask, không thu hẹp movement lock.
3. **Blind count và Inventory:** Staff được xem Inventory bình thường, nhưng khi có Audit được assign thì quantity trong scope bị mask ở các trạng thái blind-count; snapshot export bị block.
4. **Sau Submit/REOPENED:** expected quantity trong audit detail được reveal có chủ đích để Staff giải thích hoặc sửa sau khi Tenant cho phép.
5. **Outbound PENDING không giữ hàng.** Hai phiếu giống nhau có thể cùng tồn tại; duyệt là nơi chặn stale stock.
6. **Transfer PENDING cũng chưa giữ hàng.** Allocate mới tạo reservation.
7. **FE phải dùng `availableQuantity`, không chỉ `quantity`,** khi ghi nhãn “Available”. Nếu dùng raw quantity, màn hình có thể cho lập kế hoạch vượt phần chưa bị giữ; BE vẫn chặn ở Allocate/Approve.
8. **FIFO suggestion hiện là read-only snapshot.** Bước approve vẫn phải revalidate dưới lock.
9. **Manual Outbound nhiều Bin:** BE hỗ trợ bằng nhiều dòng cùng SKU, mỗi dòng một vị trí; FE cần có UX thêm allocation thay vì chỉ một dropdown.
10. **Staff Layout:** phải gọi endpoint Staff. Gọi endpoint Tenant gây 403 không có nghĩa assignment sai.
11. **Staff actions trong Tasks chỉ là gợi ý UI.** Endpoint nghiệp vụ vẫn là nguồn xác thực cuối cùng; ví dụ Audit phải đúng assignee mới đếm được.
12. **Layout mutation trong Audit:** movement lock không trực tiếp khóa API sửa layout. Khi demo không chỉnh layout song song với nghiệp vụ tồn.
13. **Legacy direct dispatch:** BE còn cho Dispatch từ PENDING/ALLOCATED để tương thích cũ; luồng thuyết trình nên luôn Allocate và Pick đầy đủ.
14. **Non-GOOD không tạo tồn kho đích.** `received` là nhận vật lý, không đồng nghĩa với `receivedGood`.
15. **Audit Start chưa kiểm tra transfer reservation đã tồn tại từ trước.** Nếu một Transfer đã Allocate/Pick rồi mới Start Audit, hàng có thể đang được giữ hoặc đã nằm ở khu staging trong khi snapshot vẫn dựa trên `stock_batches`. Quy trình vận hành nên hoàn tất/cancel các Transfer đang Allocate/Picking/Ready trước khi Start Audit. Đây là điểm BE nên siết thêm nếu muốn đảm bảo tuyệt đối.
16. **Put-away suggestion không giữ capacity.** Hai người có thể nhận cùng một gợi ý; bước tạo/duyệt Inbound hoặc Receive mới kiểm tra lại capacity thật.
17. **FIFO preview hiện lập kế hoạch từ raw batch quantity.** Transfer reservation được kiểm tra quyết định tại bước Outbound approve; vì vậy FE phải hiển thị `availableQuantity` và xử lý stale/replan.
18. **RBAC role là cấu hình dùng chung.** Admin xóa/thêm permission của `ROLE_STAFF` sẽ ảnh hưởng mọi user có role đó, không chỉ một Staff hay một Tenant. Warehouse assignment và các rule “Tenant-only/đúng assignee” vẫn tiếp tục được BE kiểm tra riêng.

## 19. Câu hỏi phản biện thường gặp

### “Tại sao Allocate không trừ kho?”

Vì Allocate là cam kết logic/giữ hàng, chưa chứng minh hàng đã rời kho. Trừ kho ở Dispatch phản ánh đúng thời điểm chuyển quyền kiểm soát vật lý.

### “Tại sao tạo hai Outbound vượt tồn vẫn được?”

Vì phiếu PENDING là nhu cầu, chưa phải giao dịch tồn. Bước duyệt lock và revalidate để chỉ giao dịch còn hợp lệ được ghi sổ. Transfer dùng reservation từ Allocate để bảo vệ hàng quan trọng hơn.

### “Staff thấy Inventory rồi Audit blind count còn ý nghĩa gì?”

Staff được xem tồn để vận hành bình thường. Khi chính Staff tham gia Audit, BE mask quantity trong phạm vi Audit và chặn snapshot export trong giai đoạn blind count. Sau Submit, số hệ thống mới được reveal để đối soát.

### “Tại sao Audit Bin lại khóa cả kho?”

Đây là trade-off an toàn của phiên bản hiện tại. Mọi đường mutation chưa dùng một ledger vị trí thống nhất, nên warehouse-level lock bảo đảm không có biến động ngoài scope làm sai fingerprint hoặc nghiệp vụ liên quan. Phiên bản sau có thể thu hẹp lock khi mọi movement đều được định tuyến theo location ledger.

### “Hàng Audit dư vượt capacity thì sao?”

Không được tự cộng tồn. Approval rollback và yêu cầu xác minh dữ liệu hoặc di chuyển vật lý. Hệ thống không hợp thức hóa một trạng thái nguy hiểm có thể làm quá tải Rack/Bin.

### “Tại sao hàng DAMAGED đã received nhưng không có trong Inventory?”

`received` xác nhận hàng vật lý đã được kiểm nhận; Inventory khả dụng chỉ chứa `GOOD`. Hàng DAMAGED/QUARANTINE/REJECTED chờ reconciliation hoặc quy trình ngoại lệ.

### “RETURN_TO_SOURCE có cộng kho nguồn ngay không?”

Không. Yêu cầu Return và Dispatch Return chỉ đổi workflow. Chỉ `receiveReturn` tại kho nguồn, có Rack/Bin hợp lệ và đủ capacity, mới tạo transaction dương.

## 20. Checklist trước buổi bảo vệ

- Dùng dữ liệu demo riêng, không dùng phiếu cũ lẫn lộn.
- Xác nhận subscription và hai warehouse contract còn ACTIVE.
- Xác nhận Tenant layout đã có Rack/Bin active.
- Xác nhận SKU có unit weight và unit volume dương.
- Xác nhận Source/Destination Staff có đúng assignment.
- Mở Network tab và bật Preserve log.
- Ghi lại tồn ban đầu theo `onHand / reserved / available`.
- Không chạy hai test mutation đồng thời nếu không chủ đích test cạnh tranh.
- Với Transfer, luôn demo đúng thứ tự Allocate -> Pick -> Dispatch -> Arrive -> Receive.
- Với Audit, chụp lại trạng thái trước Start, trong IN_PROGRESS và sau APPROVED để chứng minh lock/mutation.
- Với lỗi 409, đọc `code`, không chỉ đọc message/toast.
- Sau mỗi bước, kiểm tra cả status phiếu, Inventory và timeline/transaction.
