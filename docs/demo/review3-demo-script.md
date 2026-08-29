# Review 3 Demo Script — StockSpace

## 1. Mục tiêu và giới hạn

Kịch bản này dùng cho buổi Review 3. Mục tiêu là kể một luồng nghiệp vụ liền
mạch từ lúc người dùng có quyền truy cập kho đến lúc quản lý được tồn kho.
Kịch bản chỉ dùng behavior đã có trong release backend `5e7bf68`; không đưa
Booking, tiền cọc, dispute, handover đã retire hoặc Staff Task board vào demo.

Thời lượng đề xuất:

| Phần | Thời lượng |
|---|---:|
| Mở đầu và giới thiệu phạm vi | 3 phút |
| Live demo main flow | 32–35 phút |
| Kết luận | 2 phút |
| Q&A dự phòng | 20 phút |
| Tổng tối đa | 60 phút |

Chỉ trình diễn happy case ở luồng chính. Recovery steps bên dưới được chuẩn
bị để xử lý sự cố hoặc dùng khi hội đồng hỏi edge case.

## 2. Phân công người trình bày và demo

Phân công này chia theo câu chuyện demo, không khẳng định phạm vi contribution
của từng thành viên. Mỗi thành viên đều phải trực tiếp thực hiện ít nhất một
thao tác trên môi trường demo.

| Thứ tự | Thành viên | Vai trò trong demo | Phần trực tiếp thực hiện |
|---:|---|---|---|
| 1 | Trần Việt Anh | Dẫn chuyện và access setup | Giới thiệu, đăng nhập, chọn tenant/warehouse và xác nhận access đang active |
| 2 | Lê Quang Vinh | Layout và inbound | Mở layout, xem capacity, chuẩn bị SKU và tạo/approve inbound receipt |
| 3 | Bùi Bá Cường | Luân chuyển tồn kho | Gợi ý put-away, tạo transfer, dispatch tại nguồn và receive tại đích |
| 4 | Nguyễn Bùi Hoàng Phúc | Audit, Staff và closing | Kiểm kê, Staff operation view, public search, tổng kết limitation/next step |

Người kế tiếp chỉ chuyển sang khi người trước đã nói rõ kết quả trên màn hình.
Một người giữ vai trò điều phối thời gian và có quyền dừng thao tác nếu flow
bắt đầu lệch khỏi fixture.

## 3. Chuẩn bị trước buổi demo

### 3.1 Fixture bắt buộc

- [ ] Một tài khoản Tenant có access hợp lệ và WMS subscription đang active.
- [ ] Tenant có hai warehouse khác nhau, cùng thuộc một tenant, có contract
      access active.
- [ ] Warehouse A có layout owner/default hợp lệ theo mét và ít nhất một bin
      đang chứa stock của SKU demo.
- [ ] Warehouse B có layout riêng và ít nhất một bin trống phù hợp để nhận stock.
- [ ] SKU demo thuộc tenant, đang active, có `unitWeightKg` và `unitVolumeM3`
      dương; không dùng SKU thiếu physical metadata.
- [ ] Có một Staff account active, được assign vào warehouse cần demo và còn
      contract access.
- [ ] Có dữ liệu public warehouse để search theo location, capacity và price.
- [ ] Chuẩn bị trước các UUID fixture trong một file riêng của nhóm; không ghi
      access token, mật khẩu hoặc dữ liệu nhạy cảm vào repository.

### 3.2 API/UI readiness gate

Trước khi chạy rehearsal, kiểm tra FE đã gọi được các endpoint sau:

- [ ] `GET /api/tenant/warehouses/{warehouseId}/layout/capacity`
- [ ] `POST /api/tenant/inventory/putaway/suggestions`
- [ ] `/api/tenant/inventory/transfers` lifecycle
- [ ] `/api/staff/operations`
- [ ] Inventory Audit lifecycle
- [ ] Warehouse search với tên filter hiện tại

Nếu một màn hình FE chưa tích hợp kịp, không giả nhận là UI đã hoàn thiện. Dùng
Swagger/Postman với cùng fixture để kiểm tra backend contract, ghi rõ phần đó
là backend/API demonstration và chuyển sang màn hình kế tiếp theo recovery plan.

## 4. Main flow theo thứ tự

### Step 1 — Login and establish active access

**Người thực hiện:** Trần Việt Anh — khoảng 4 phút.

1. Mở hệ thống và đăng nhập bằng tài khoản Tenant fixture.
2. Chọn tenant hiện tại và warehouse A.
3. Nói ngắn gọn: WMS mutation cần access hợp lệ; backend vẫn kiểm tra tenant,
   contract, subscription và warehouse scope, không chỉ dựa vào UI.
4. Mở danh sách warehouse tenant để xác nhận warehouse A và B đều thuộc phạm
   vi tenant cần demo.

**Kết quả cần nói:** “Tenant đã có quyền sử dụng hai warehouse trong cùng tenant;
chúng ta bắt đầu từ layout và dữ liệu thực tế của từng kho.”

### Step 2 — View layout and physical capacity

**Người thực hiện:** Lê Quang Vinh — khoảng 5 phút.

1. Chọn warehouse A và mở layout tenant.
2. Cho xem rack/bin, kích thước thực tế theo mét và trạng thái occupied.
3. Mở capacity metrics của warehouse A.
4. Chỉ vào `currentWeightKg`, `currentVolumeM3`, remaining capacity,
   utilization và `capacityStatus` của rack/bin.
5. Chọn một bin có `storedSkus` để chứng minh số liệu lấy từ stock hiện tại,
   không phải chỉ tô màu 3D.

**Endpoint backend:**

```http
GET /api/tenant/warehouses/{warehouseId}/layout
GET /api/tenant/warehouses/{warehouseId}/layout/capacity
```

**Kết quả cần nói:** “Layout là không gian vật lý; capacity là read model tính từ
physical metadata của SKU và stock hiện tại. API không tự reserve hoặc tự thay
đổi tồn kho.”

### Step 3 — Prepare SKU and receive inbound stock

**Người thực hiện:** Lê Quang Vinh — khoảng 7 phút.

1. Mở danh mục SKU của tenant và chọn SKU demo đã có `unitWeightKg` và
   `unitVolumeM3`.
2. Tạo inbound quantity nhỏ, đủ để demo nhưng không làm fixture vượt capacity.
3. Gọi put-away suggestion với context `INBOUND`.
4. Hiển thị rack/bin được gợi ý, quantity, reasons, capacity snapshot và
   `unallocatedQuantity`.
5. Xác nhận allocation đầy đủ trong happy case.
6. Tạo inbound receipt bằng allocation đã xác nhận.
7. Tenant approve receipt.
8. Tải lại capacity/stock để cho thấy current load và quantity đã thay đổi.

**Endpoint backend:**

```http
POST /api/tenant/inventory/putaway/suggestions
POST /api/tenant/inventory/receipts
PATCH /api/tenant/inventory/receipts/{receiptId}/approve
GET /api/tenant/warehouses/{warehouseId}/layout/capacity
```

**Kết quả cần nói:** “Put-away chỉ là recommendation deterministic; stock chỉ
thay đổi khi receipt được approve. Backend kiểm tra lại capacity ở mutation vì
suggestion có thể stale.”

### Step 4 — Suggest destination put-away and transfer stock

**Người thực hiện:** Bùi Bá Cường — khoảng 8 phút.

1. Chọn một phần quantity của SKU đang ở warehouse A.
2. Gọi put-away suggestion cho warehouse B với context `TRANSFER_RECEIVE` để
   lấy destination rack/bin phù hợp.
3. Tạo transfer từ warehouse A sang B, truyền source batch/rack/bin thật và
   quantity đúng.
4. Mở transfer, xác nhận trạng thái ban đầu là `PENDING`.
5. Approve dispatch. Cho thấy trạng thái thành `IN_TRANSIT` và stock nguồn đã
   bị trừ một lần.
6. Receive tại warehouse B bằng destination allocation từ layout B.
7. Cho thấy trạng thái `COMPLETED`, inbound allocation và stock tại B.
8. Nhắc rõ không map bin A sang bin B; hai layout là độc lập.

**Endpoint backend:**

```http
POST /api/tenant/inventory/putaway/suggestions
POST /api/tenant/inventory/transfers
PATCH /api/tenant/inventory/transfers/{transferId}/approve-dispatch
POST /api/tenant/inventory/transfers/{transferId}/receive
GET /api/tenant/inventory/stock?warehouseId={warehouseId}
```

**Kết quả cần nói:** “Transfer là một workflow riêng. Dispatch trừ stock nguồn,
receive cộng stock đích; không tạo hai receipt độc lập để mô phỏng transfer.”

### Step 5 — Perform inventory audit

**Người thực hiện:** Nguyễn Bùi Hoàng Phúc — khoảng 5 phút.

1. Chọn warehouse B và tạo audit.
2. Mở các audit items được snapshot từ stock.
3. Nhập actual quantity đúng với expected quantity cho happy case.
4. Submit audit và cho thấy trạng thái `SUBMITTED`.
5. Tenant approve audit và cho thấy trạng thái `APPROVED`.
6. Nói rõ nếu có discrepancy, approval mới là boundary để tạo adjustment và
   cập nhật stock; tạo/submit chưa làm thay đổi tồn kho.

**Endpoint backend:**

```http
POST /api/tenant/inventory/audits
POST /api/tenant/inventory/audits/{auditId}/submit
PATCH /api/tenant/inventory/audits/{auditId}/approve
```

**Kết quả cần nói:** “Audit có lifecycle độc lập và lưu expected/actual/
discrepancy; việc reconcile stock chỉ xảy ra ở bước approve.”

### Step 6 — Show Staff operation scope

**Người thực hiện:** Nguyễn Bùi Hoàng Phúc — khoảng 4 phút.

1. Chuyển sang Staff account đã được assign warehouse B.
2. Mở work history và lọc assignment `ACTIVE`.
3. Mở danh sách operations theo warehouse B.
4. Cho thấy operation type có thể là `RECEIPT`, `AUDIT`, `TRANSFER` và status
   là status thật của module tương ứng.
5. Mở layout read-only của warehouse được assign.
6. Nếu demo tiếp thao tác Staff, chỉ dùng action module đã có; không nói đây là
   Staff Task board hoặc workflow mới.

**Endpoint backend:**

```http
GET /api/staff/my-work-history
GET /api/staff/operations?warehouseId={warehouseId}&page=0&size=20
GET /api/staff/warehouses/{warehouseId}/layout
```

**Kết quả cần nói:** “Staff chỉ thấy warehouse được assign và còn access. Backend
trả một read-only projection từ Receipt/Audit/Transfer; mutation vẫn do module
gốc xử lý.”

### Step 7 — Public warehouse search and closing

**Người thực hiện:** Trần Việt Anh hoặc Hoàng Phúc — khoảng 3 phút.

1. Đăng xuất hoặc mở public search theo fixture.
2. Lọc theo location, warehouse type, khoảng capacity và khoảng rental price.
3. Mở detail/layout của một warehouse public.
4. Kết luận bằng ba điểm: số liệu physical capacity, recommendation có giải
   thích, và transfer/audit có lifecycle rõ ràng.

**Endpoint backend:**

```http
GET /api/warehouses?provinceCode=...&districtCode=...&warehouseTypeId=...&minCapacity=...&maxCapacity=...&minRentalPrice=...&maxRentalPrice=...
GET /api/warehouses/{warehouseId}
```

Không dùng `minPrice`/`maxPrice` trong code FE mới; đó chỉ là compatibility alias
trong thời gian chuyển tiếp.

## 5. Recovery plan khi demo gặp lỗi

| Tình huống | Cách xử lý trong buổi demo | Không được làm |
|---|---|---|
| Login/access/assignment lỗi | Chuyển sang tài khoản fixture dự phòng và nói rõ access prerequisite | Không tắt authorization hoặc đổi dữ liệu ngẫu nhiên giữa demo |
| Suggestion có `unallocatedQuantity` | Giảm quantity theo fixture hoặc chọn bin khác rồi request lại | Không nói đã put-away đủ khi còn quantity chưa phân bổ |
| Allocation bị stale/capacity changed | Reload capacity, request suggestion mới, xác nhận lại | Không retry mù allocation cũ |
| Transfer không ở trạng thái mong đợi | Mở detail, đọc status hiện tại và dùng fixture transfer khác | Không gọi receive trực tiếp khi chưa `IN_TRANSIT` |
| Audit đã dùng trong rehearsal | Dùng audit fixture mới hoặc reset dữ liệu được nhóm chuẩn bị trước | Không sửa trực tiếp database production trong lúc demo |
| FE chưa có màn hình cho API mới | Dùng Swagger/Postman theo đúng request/response và đánh dấu API demo | Không dựng mock data rồi gọi đó là behavior thật |

## 6. Câu hỏi dự kiến và câu trả lời ngắn

**Put-away có tự động đưa hàng vào bin không?**

Không. Backend chỉ trả recommendation; user xác nhận allocation rồi gọi receipt
hoặc transfer receive. Mutation mới cập nhật stock.

**Tại sao không map bin nguồn và bin đích?**

Hai warehouse có layout độc lập. Hàng rời nguồn ở dispatch và được chọn vị trí
mới ở destination khi receive.

**Nếu Staff gọi API của warehouse không được assign?**

Backend trả `403` sau khi kiểm tra active assignment, contract và warehouse scope.
FE chỉ dùng assignment để hiển thị; không coi nó là authorization cuối cùng.

**Audit tạo hoặc submit có đổi tồn kho ngay không?**

Không. Audit snapshot và lưu actual trước; chỉ approve mới reconcile discrepancy.

**Hệ thống đã có full 3D packing hoặc tối ưu tuyến xe nâng chưa?**

Chưa. Phiên bản hiện tại dùng heuristic deterministic dựa trên weight/volume,
capacity, SKU đang có trong bin và tie-break ổn định. Đây là limitation đã nhận
thức rõ và là hướng phát triển tiếp theo.

## 7. Rehearsal sign-off

- [ ] Mỗi thành viên đã trực tiếp demo phần được phân công.
- [ ] Flow chạy đúng thứ tự, không cần Booking/deposit.
- [ ] Hai warehouse và cùng một SKU cho thấy stock source/destination đúng.
- [ ] Capacity và put-away dùng API backend, không dùng số liệu mock.
- [ ] Transfer đi đủ `PENDING → IN_TRANSIT → COMPLETED`.
- [ ] Audit đi đủ `PENDING → SUBMITTED → APPROVED`.
- [ ] Staff chỉ thấy warehouse assignment hợp lệ.
- [ ] Search dùng current filter names.
- [ ] Thời lượng rehearsal nằm trong giới hạn 40 phút presentation, chừa tối đa
      20 phút cho Q&A.

## 8. Release and evidence record

| Item | Value |
|---|---|
| Backend release commit | `5e7bf68` |
| Frontend commit checked during documentation | `cda9324` |
| Fixture IDs | Điền trong file nội bộ của nhóm trước rehearsal |
| Rehearsal date/time | Điền sau khi chạy thử |
| Actual duration | Điền sau khi chạy thử |
| Known FE integration gaps | Capacity, put-away, transfer và Staff Operations cần xác nhận readiness theo Section 3.2 |
