# StockSpace — Luồng và dữ liệu test trên web

> Cập nhật theo web production ngày **04/09/2026** (Asia/Ho_Chi_Minh).
>
> Web: <https://stock-space-nu.vercel.app>
>
> Đây là dữ liệu demo. Không dùng tài khoản ngân hàng, email hoặc số điện thoại thật của người khác.

## 1. Tài khoản demo

Mở trang chủ và bấm **SIGN IN**. Web không có route `/login` riêng; đăng nhập được mở bằng popup từ trang chủ.

| Vai trò | Email | Mật khẩu | Trạng thái hiện tại |
|---|---|---|---|
| Admin | `admin@stockspace.com` | `Password123` | Dùng được |
| Owner | `owner@stockspace.com` | `Password123` | Dùng được, có 2 kho và số dư ví |
| Tenant | `tenant@stockspace.com` | `Password123` | Dùng được, có hợp đồng và gói WMS active |
| Staff | `staff@stockspace.com` | `Password123` | Đăng nhập được nhưng **chưa được gán tenant/kho** |
| Inspector | `inspector@stockspace.com` | `Password123` | Đăng nhập được nhưng hiện **chưa có inspection được giao** |

## 2. Fixture đang có sẵn

### 2.1 Kho và hợp đồng

| Tên | ID | Loại | Kích thước layout | Ghi chú |
|---|---|---|---|---|
| Kho Vũng Tàu | `c31808fc-7222-4d23-8910-c87961a105d0` | Cold | `15 × 20 × 10 m` | Kho A, có 2 rack/4 bin |
| Kho Bà Rịa | `cecfe485-dd53-4a54-a3c5-5d4529ef34f2` | Normal | `30 × 40 × 10 m` | Kho B, có 1 rack/1 bin |

Hai hợp đồng của Tenant hiện có trạng thái `ACTIVE`:

| Kho | Contract ID | Bắt đầu | Kết thúc |
|---|---|---|---|
| Kho Vũng Tàu | `0a5c2dfc-c275-427e-bff1-cd4b346c018b` | 31/08/2026 | 07/09/2026 |
| Kho Bà Rịa | `c579aa20-d801-4f89-8f62-9d3baecbb390` | 31/08/2026 | 07/09/2026 |

**Lưu ý:** sau ngày 07/09/2026, các luồng WMS phụ thuộc hợp đồng có thể trả `403`. Khi đó phải tạo/xác nhận hợp đồng mới trước khi test tiếp.

### 2.2 Category, SKU và UOM

| Loại | Tên/mã | ID | Physical metadata |
|---|---|---|---|
| Category | Nước Ngọt | `03cf31b8-d42b-45a5-8206-25567738bf12` | — |
| Category | Điện Tử | `994888f2-de24-4bde-b9bc-591512aa3f51` | — |
| SKU | `CC_001` — Coca | `620ab915-79e6-4b1a-9a3e-50dc9f6c710f` | `25 kg`, `25 m³`/đơn vị |
| SKU | `IP-001` — IPHONE 15 PROMAX | `461c312f-77bf-4327-a6d4-9c98ba4df994` | `10 kg`, `10 m³`/đơn vị |
| UOM | `THUNG` — Thùng | `5348974c-425b-49d5-b638-83322134ae78` | — |

Tồn kho tại thời điểm chụp fixture:

| Kho | SKU | Tổng số lượng có thể dùng |
|---|---|---:|
| Kho Vũng Tàu | `CC_001` | 2 |
| Kho Vũng Tàu | `IP-001` | 8 |
| Kho Bà Rịa | `CC_001` | 2 |

Kho Vũng Tàu gần đầy theo giới hạn volume từng bin. Để test inbound an toàn, ưu tiên **Kho Bà Rịa + IP-001 + quantity 1** hoặc tạo SKU demo nhỏ ở luồng 9.

### 2.3 Ví, subscription và package

| Dữ liệu | Giá trị tại 04/09/2026 |
|---|---|
| Ví Owner | `199.870.000 ₫` |
| Ví Tenant | `99.500.000 ₫` |
| Gói WMS của Tenant | Gói Nâng Cao (Advanced) |
| Thời hạn gói WMS | 31/08/2026 → 30/09/2026 |
| Giới hạn staff | 10 |
| Phí inspection hiện tại | `40.000 ₫` |

Gói đăng bài kho:

| Gói | ID | Thời hạn | Giá |
|---|---|---:|---:|
| Basic | `9d64c0de-025c-4d99-a9a5-5f581cd6a2b1` | 10 ngày | `20.000 ₫` |
| Advance | `7114e155-6391-4de5-91ce-45ba6b506324` | 15 ngày | `40.000 ₫` |
| Pro | `3655e106-a8d0-4b46-b1b2-00e6f5af1ff0` | 30 ngày | `70.000 ₫` |

## 3. Thứ tự chạy full flow khuyến nghị

Chạy theo thứ tự sau để không thiếu dữ liệu tiền đề:

1. Đăng ký/đăng nhập.
2. Owner tạo kho.
3. Owner tạo layout.
4. Owner gửi kho để Admin duyệt; Admin approve.
5. Owner yêu cầu inspection; Admin assign; Inspector nộp report.
6. Owner mua gói đăng bài; người dùng public tìm kho.
7. Owner tạo và submit hợp đồng; Tenant confirm.
8. Tenant kiểm tra/mua subscription WMS.
9. Tenant tạo category và SKU.
10. Tenant tạo/approve inbound.
11. Tenant kiểm tra tồn kho và tạo/approve outbound.
12. Tenant tạo/submit/approve audit.
13. Tenant mời Staff và gán kho; Staff đăng nhập thao tác.
14. Test ví nạp/rút tiền.

## 4. Flow 1 — Đăng ký tài khoản mới

### Tenant

| Field | Data test |
|---|---|
| Role | `I'm a Tenant` |
| Full Name | `Nguyen Minh Tenant QA` |
| Email | Dùng email QA do nhóm sở hữu, ví dụ `stockspace.qa+tenant0904@your-domain.com` |
| Password | `Demo@123456` |
| Confirm Password | `Demo@123456` |
| Phone Number | `0901234567` |
| Terms | Tick đồng ý |

### Owner

Giữ nguyên data trên, đổi role thành `I'm an Owner`, tên thành `Nguyen Minh Owner QA`, email thành một địa chỉ QA khác.

Kết quả mong đợi: tạo tài khoản thành công, tự đăng nhập và chuyển đến dashboard đúng role. Mỗi lần chạy phải đổi suffix email vì email là duy nhất.

## 5. Flow 2 — Public tìm và xem kho

Không cần đăng nhập.

1. Bấm **VIEW WAREHOUSES**.
2. Search lần lượt `Vũng Tàu` và `Bà Rịa`.
3. Min Capacity nhập `1000`.
4. Mở chi tiết `Kho Vũng Tàu`.
5. Kiểm tra ảnh, địa chỉ, loại kho, diện tích, trạng thái verified và layout public.

Kết quả mong đợi: thấy hai kho fixture ở mục 2.1.

## 6. Flow 3 — Owner tạo kho mới

Đăng nhập Owner → **Post Warehouse**.

| Field | Data test |
|---|---|
| Warehouse name | `Kho Demo QA 0904-01` |
| City | `Ho Chi Minh City` |
| Ward/Commune | `Phường Tân Thuận` — code `27478` |
| Street address | `123 Đường Nguyễn Văn Quỳ, Khu demo QA` |
| Warehouse type | `Normal` |
| Description | `Kho demo phục vụ kiểm thử luồng duyệt, kiểm định, hợp đồng và WMS.` |
| Layout width | `30` m |
| Layout length | `40` m |
| Layout height | `8` m |
| Capacity | `1200` m² |
| Pricing type | `FIXED_MONTHLY` |
| Rental price | `12000000` |
| Cover image | 1 ảnh `.jpg`/`.png` kho do nhóm sở hữu |
| Related images | 2–3 ảnh kho do nhóm sở hữu |

Mỗi lần chạy lại đổi `0904-01` thành suffix mới. Kết quả mong đợi: kho được tạo ở trạng thái `DRAFT`.

## 7. Flow 4 — Owner tạo layout

Owner → **Warehouse Layout** → chọn kho vừa tạo.

### Layout

| Field | Data test |
|---|---:|
| Width | `30` m |
| Length | `40` m |
| Height | `8` m |

### Rack

| Field | Data test |
|---|---|
| Name | `Rack Demo 01` |
| Code | `RACK-DEMO-01` |
| Max weight | `1000` kg |
| Max volume | `100` m³ |
| X / Y / Z | `1` / `1` / `0` |
| Width / Length / Height | `10` / `20` / `6` m |
| Shelf count | `2` |

### Hai bin trong rack

| Field | Bin 1 | Bin 2 |
|---|---|---|
| Name | `Bin Demo 01` | `Bin Demo 02` |
| Code | `BIN-DEMO-01` | `BIN-DEMO-02` |
| Shelf level | `1` | `2` |
| Max weight | `500` kg | `500` kg |
| Max volume | `50` m³ | `50` m³ |
| X / Y / Z | `1 / 1 / 0` | `1 / 11 / 0` |
| Width / Length / Height | `8 / 8 / 2.5` m | `8 / 8 / 2.5` m |

Kết quả mong đợi: save layout thành công, không có rack/bin nằm ngoài layout hoặc chồng lấn nhau.

## 8. Flow 5 — Duyệt kho

1. Owner → **Warehouse List** → menu ba chấm của kho mới → **Submit for approval**.
2. Đăng xuất, đăng nhập Admin.
3. Admin → **Warehouses Management** hoặc **Warehouses Approval**.
4. Search `Kho Demo QA 0904-01`.
5. Mở chi tiết và bấm **Approve**.

Data khi test nhánh reject:

| Field | Data test |
|---|---|
| Reject reason | `Ảnh kho chưa thể hiện rõ lối thoát hiểm và khu vực lưu trữ.` |

Kết quả happy path: kho chuyển từ `DRAFT` → `PENDING` → `APPROVED/READY` theo label trên web.

## 9. Flow 6 — Kiểm định kho

1. Owner mở kho vừa được duyệt → **Request Inspection**.
2. Xác nhận phí `40.000 ₫`.
3. Admin → **Inspections** → chọn yêu cầu `PENDING`.
4. Assign cho `inspector@stockspace.com`.
5. Inspector đăng nhập → **My Inspections** → mở yêu cầu.
6. Điền report và submit.

Data report happy path:

| Field | Data test |
|---|---|
| Status | `PASSED` |
| Notes | `Kho đạt yêu cầu demo: kết cấu ổn định, lối thoát hiểm thông thoáng, khu vực lưu trữ sạch và đủ biển báo.` |
| Checklist/PCCC | `Đạt` |
| Checklist/kết cấu | `Đạt` |
| Checklist/vệ sinh | `Đạt` |
| Images | 1–2 ảnh minh chứng do nhóm sở hữu |

Data nhánh fail:

| Field | Data test |
|---|---|
| Status | `FAILED` |
| Notes | `Chưa đạt: thiếu biển chỉ dẫn lối thoát hiểm và bình chữa cháy tại khu vực rack demo.` |

Kết quả happy path: `PENDING → IN_PROGRESS → PASSED`; kho được hiển thị đã kiểm định.

## 10. Flow 7 — Mua gói đăng bài và public kho

Owner mở kho đã duyệt/kiểm định → chọn gói đăng bài.

| Field | Data test |
|---|---|
| Package | `Basic` |
| Package ID | `9d64c0de-025c-4d99-a9a5-5f581cd6a2b1` |
| Start date | `04/09/2026` hoặc ngày hiện tại khi chạy |
| Expected fee | `20.000 ₫` |

Kết quả mong đợi: ví Owner bị trừ đúng một lần, order publication active/scheduled và kho xuất hiện ở trang public đúng thời gian.

## 11. Flow 8 — Hợp đồng Owner → Tenant

Chỉ chạy với kho chưa có hợp đồng active. Owner → **Contracts** hoặc menu kho → tạo contract draft.

| Field | Data test |
|---|---|
| Warehouse | Kho demo vừa tạo |
| Tenant email | `tenant@stockspace.com` |
| Start date | `05/09/2026` |
| End date | `05/10/2026` |
| Leased width | `10` m |
| Leased length | `15` m |
| Leased height | `6` m |
| Negotiated monthly rent | Để trống nếu kho dùng `FIXED_MONTHLY`; nhập `12000000` nếu kho là `NEGOTIATED` |
| Owner note | `Hợp đồng demo QA; Tenant sử dụng đúng khu vực layout được cấp.` |
| Paper contract | Ít nhất 1 file/ảnh hợp đồng demo do nhóm sở hữu |

Các bước:

1. Owner **Save Draft**.
2. Owner kiểm tra preview/layout rồi **Submit**.
3. Tenant đăng nhập → **My Contracts** → mở hợp đồng.
4. Happy path: Tenant **Confirm**.
5. Nhánh request changes dùng lý do: `Đề nghị điều chỉnh ngày kết thúc và bổ sung mô tả khu vực thuê.`
6. Nhánh reject dùng lý do: `Điều khoản và diện tích thuê chưa đúng thỏa thuận.`

Kết quả happy path: `DRAFT → PENDING_TENANT → ACTIVE`. Chỉ khi contract `ACTIVE`, Tenant mới có quyền WMS trên kho.

## 12. Flow 9 — Subscription WMS

Tenant → **Subscription**.

Fixture hiện có gói Advanced active đến 30/09/2026 nên chỉ cần kiểm tra thông tin, không mua lại.

Nếu test bằng Tenant mới:

| Field | Data test |
|---|---|
| Package | `Gói Cơ Bản (Basic)` |
| Giá | `200.000 ₫` |
| Thời hạn | `30 ngày` |
| Max staff | `2` |

Kết quả mong đợi: ví bị trừ đúng giá gói và subscription chuyển `ACTIVE`.

## 13. Flow 10 — Tạo Category và SKU

Tenant → **Category Mgt** → **Add Category**.

| Field | Data test |
|---|---|
| Category name | `Hàng Gia Dụng Demo 0904-01` |

Tenant → **SKU Mgt** → **Add SKU**.

| Field | Data test |
|---|---|
| Product Name | `Ấm Siêu Tốc Demo` |
| SKU Code | `DEMO-KETTLE-0904-01` |
| Category | `Hàng Gia Dụng Demo 0904-01` |
| Unit of Measure | `THUNG — Thùng` |
| Unit weight | `2` kg |
| Unit volume | `0.02` m³ |
| Specification key/value | `color` / `white` |
| Specification key/value | `power_watt` / `1500` |

Kết quả mong đợi: SKU xuất hiện trong danh sách. Mỗi lần chạy lại đổi suffix để tránh trùng mã.

## 14. Flow 11 — Inbound

### Cách chạy ngay bằng fixture hiện tại

Tenant → **Inbound**.

| Field | Data test |
|---|---|
| Warehouse | `Kho Bà Rịa` |
| Product/SKU | `IP-001 — IPHONE 15 PROMAX` |
| Total Quantity | `1` |
| Rack | `Rack 1` |
| Bin | `Bin 1` |
| Note | `Inbound demo QA 0904 — 1 thùng IP-001.` |

Không chọn Kho Vũng Tàu cho inbound `CC_001`; các bin hiện gần đầy theo volume và một đơn vị Coca cần tới `25 m³`.

Các bước:

1. Chọn kho trước.
2. Bấm **New**.
3. Chọn SKU và quantity.
4. Phân bổ đủ số lượng vào bin; kiểm tra `Allocated = Total Quantity`.
5. **Confirm Inbound** để tạo phiếu `PENDING`.
6. Mở menu phiếu và **Approve**.

Kết quả mong đợi: `PENDING → APPROVED`; số lượng IP-001 tại Kho Bà Rịa tăng 1. Không approve lặp lại.

## 15. Flow 12 — Inventory và Outbound

Tenant → **Inventory** → chọn `Kho Vũng Tàu` để kiểm tra tồn kho.

Tenant → **Outbound**:

| Field | Data test |
|---|---|
| Warehouse | `Kho Vũng Tàu` |
| Product/SKU | `IP-001 — IPHONE 15 PROMAX` |
| Total Quantity | `1` |
| Pick quantity | `1` từ batch/bin được web gợi ý |
| Note | `Outbound demo QA 0904 — xuất 1 thùng IP-001.` |

Các bước:

1. Chọn kho trước, sau đó bấm **New**.
2. Chọn SKU; không tự nhập batch không thuộc kho.
3. Pick đủ `1/1`.
4. **Confirm Outbound** để tạo `PENDING`.
5. Mở menu phiếu và **Approve**.

Kết quả mong đợi: tồn IP-001 tại Kho Vũng Tàu giảm từ tổng hiện tại đúng 1 đơn vị. Nếu có người đã test trước, lấy số hiển thị trên Inventory làm baseline mới.

## 16. Flow 13 — Inventory Audit

Tenant → **Audits** → **New audit**.

| Field | Data test |
|---|---|
| Warehouse | `Kho Vũng Tàu` |
| Note | `Kiểm kê happy path QA 0904; số thực tế bằng số hệ thống.` |

Các bước:

1. Create audit; hệ thống snapshot các batch hiện tại.
2. Mở audit vừa tạo.
3. Với **từng dòng**, nhập `Actual Quantity` đúng bằng `Expected Quantity` đang hiển thị.
4. Note dòng: `Đã đếm thực tế, khớp hệ thống.`
5. **Submit**.
6. Tenant **Approve**.

Kết quả mong đợi: `PENDING → SUBMITTED → APPROVED`, discrepancy bằng 0 và tồn kho không đổi.

Nhánh discrepancy chỉ nên chạy trên QA riêng:

- Chọn một batch có expected > 0.
- Nhập actual = expected - 1.
- Note: `Thiếu 1 đơn vị khi kiểm đếm.`
- Khi approve, hệ thống tạo adjustment và giảm tồn thật.

## 17. Flow 14 — Mời và gán Staff

Tenant → **Staff** → **Invite Staff**.

| Field | Data test |
|---|---|
| Email | Email QA mà nhóm có thể mở hộp thư, ví dụ `stockspace.qa+staff0904@your-domain.com` |
| Full name | `Le Staff Demo QA` |
| Phone | `0907654321` |

Luồng đầy đủ:

1. Tenant gửi invite; link có hiệu lực 48 giờ.
2. Mở email QA → mở link invite.
3. Staff đặt mật khẩu `DemoStaff@2026`.
4. Tenant mở staff vừa tạo → **Assign Warehouse**.
5. Warehouse: `Kho Vũng Tàu`.
6. Custom title: `Thủ kho Demo`.
7. Notes: `Phụ trách inbound, outbound và kiểm kê ca sáng.`
8. Staff đăng nhập bằng email QA vừa tạo.

Kết quả mong đợi: Career History có assignment `ACTIVE`; Staff thấy đúng kho được gán.

**Không dùng `staff@stockspace.com` để kiểm tra warehouse scope hiện tại**, vì tài khoản seed này chưa được gán vào tenant/kho nào.

## 18. Flow 15 — Staff thao tác WMS

Chỉ chạy sau Flow 14 bằng Staff đã được gán kho.

| Luồng | Data test |
|---|---|
| Xem layout | `Kho Vũng Tàu` |
| Inbound | `DEMO-KETTLE-0904-01`, quantity `1`, bin còn capacity |
| Outbound | `IP-001`, quantity `1` |
| Audit | Actual = Expected cho từng dòng |

Kết quả mong đợi: Staff chỉ thấy kho đang có assignment `ACTIVE`; truy cập kho khác phải bị từ chối.

## 19. Flow 16 — Nạp tiền VNPAY sandbox

Tenant → **Wallet** → **Top Up**.

| Field | Data test |
|---|---|
| Amount | `100000` |
| Bank | `NCB` |
| Card number | `9704198526191432198` |
| Card holder | `NGUYEN VAN A` |
| Issue date | `07/15` |
| OTP | `123456` |

Thông tin thẻ trên là thẻ test do VNPAY công bố cho môi trường sandbox: <https://sandbox.vnpayment.vn/apis/docs/gioi-thieu/>.

Kết quả mong đợi: sau callback thành công, ví tăng `100.000 ₫` và lịch sử có transaction thành công. Nếu redirect về web nhưng số dư chưa tăng, kiểm tra callback/IPN trước khi bấm thanh toán lại.

## 20. Flow 17 — Rút tiền

Owner hoặc Tenant → **Wallet** → **Withdraw money**.

| Field | Data test |
|---|---|
| Amount | `50000` |
| Bank name | `NCB TEST` |
| Account number | `9704198526191432198` |
| Account holder name | `NGUYEN VAN A` |

Các bước:

1. Tạo withdrawal request.
2. Admin → **Withdrawals** → tìm request mới.
3. Happy path: Admin approve, note `Approved for QA flow 0904`.
4. Reject path: Admin reject, note `Thông tin tài khoản test không hợp lệ.`

Chỉ approve trên môi trường demo/sandbox. Không dùng thông tin ngân hàng thật trong tài liệu chia sẻ.

## 21. Flow 18 — Admin quản trị master data

### Tạo loại kho

Admin → **Warehouse Types** → **Add Type**.

| Field | Data test |
|---|---|
| Name | `Kho Demo QA 0904-01` |
| Description | `Loại kho chỉ dùng cho kiểm thử giao diện và phân loại.` |

### Tìm user

Admin → **Users** → search lần lượt:

- `tenant@stockspace.com`
- `owner@stockspace.com`
- Email QA được tạo ở Flow 1 hoặc Flow 14.

Không deactivate/delete các tài khoản seed trong bảng mục 1.

### System config

Admin → **System Config**. Với production chỉ kiểm tra:

- `inspection_fee = 40000`
- `contract_expiry_days = 7`

Không thay đổi config production chỉ để chụp demo.

## 22. Flow chưa nên test trực tiếp trên web production

| Flow | Lý do/trạng thái hiện tại |
|---|---|
| Stock Transfer A → B | Backend có API nhưng frontend production chưa có route/màn hình transfer riêng |
| Booking cũ | Menu frontend còn hiển thị nhưng Review 3 backend đã loại Booking khỏi main flow |
| Dispute/handover cũ | Route frontend còn tồn tại; backend hiện tại đã retire một phần permission/behavior |
| Staff dashboard seed | Dashboard hiện hiển thị activity mẫu dù `staff@stockspace.com` chưa có assignment; dùng Career History để xác nhận scope thật |
| Inspector flow ngay lập tức | Inspector hiện có 0 assignment; phải tạo request và Admin assign trước |

## 23. Checklist trước mỗi lần test

- [ ] Đổi suffix `0904-01` nếu category/SKU/kho/email đã tồn tại.
- [ ] Kiểm tra contract vẫn `ACTIVE` và chưa qua `endDate`.
- [ ] Kiểm tra subscription WMS vẫn `ACTIVE`.
- [ ] Chọn warehouse trước khi tạo inbound/outbound.
- [ ] Đọc capacity từng bin, không chỉ nhìn capacity tổng của warehouse.
- [ ] Ghi lại số lượng tồn trước và sau khi approve.
- [ ] Không approve cùng một receipt/audit/withdrawal hai lần.
- [ ] Không mời email hoặc dùng số điện thoại của người thật ngoài nhóm.
- [ ] Không deactivate/delete tài khoản seed.
- [ ] Các flow trừ tiền hoặc thay đổi tồn thật chỉ chạy một lần trên production demo.

## 24. Một kịch bản test nhanh 10 phút bằng fixture có sẵn

1. Login Tenant bằng `tenant@stockspace.com` / `Password123`.
2. Mở **My Contracts**, xác nhận hai contract `ACTIVE`.
3. Mở **Subscription**, xác nhận Advanced còn hạn.
4. Mở **Inventory**, chọn Kho Vũng Tàu, ghi lại tổng IP-001.
5. Tạo inbound tại **Kho Bà Rịa**, SKU `IP-001`, quantity `1`, rồi approve.
6. Tải lại Inventory tại Kho Bà Rịa, xác nhận IP-001 tăng 1.
7. Tạo outbound tại Kho Vũng Tàu, SKU `IP-001`, quantity `1`, rồi approve.
8. Tải lại Inventory tại Kho Vũng Tàu, xác nhận IP-001 giảm 1.
9. Tạo audit Kho Vũng Tàu; nhập actual bằng expected; submit và approve.
10. Đăng xuất; login Admin và kiểm tra WMS Inventory/WMS Audits thấy dữ liệu vừa tạo.
