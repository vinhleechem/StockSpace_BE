# Kế hoạch refactor luồng thuê kho sau Review 3

## 1. Mục tiêu và phạm vi

StockSpace được định vị là:

- Nền tảng đăng tin và kết nối Tenant với Warehouse Owner.
- Nơi lưu, xác nhận và theo dõi bản số hóa của hợp đồng hai bên đã ký ngoài hệ thống.
- Hệ thống cho Tenant quan sát Contract và layout được cấp sau khi hợp đồng có hiệu lực; các chức năng quản lý hậu thuê chỉ được mở khi Tenant đồng thời có gói dịch vụ còn hiệu lực.

StockSpace **không** chịu trách nhiệm:

- Giữ chỗ hoặc khóa kho khi Tenant mới bày tỏ nhu cầu.
- Thu tiền cọc thuê kho, hoàn cọc hoặc phân xử tiền cọc.
- Xác minh hai hợp đồng của các Tenant khác nhau có chồng lấn không gian vật lý hay không.
- Thay thế hợp đồng giấy hoặc hoạt động thương lượng trực tiếp giữa hai bên.
- Quản lý trạng thái bắt buộc cho bước “gặp mặt/trao đổi ngoài hệ thống”.

## 2. Luồng nghiệp vụ được chốt

```text
Tenant đăng nhập
→ Xem bài đăng và thông tin liên hệ Owner
→ Hai bên trao đổi, xem kho và ký hợp đồng giấy ngoài hệ thống
→ Owner tạo Digital Contract cho đúng Tenant
→ Owner đính kèm ảnh/tệp hợp đồng giấy và thiết lập layout phần Tenant sử dụng
→ Owner gửi Digital Contract
→ Tenant kiểm tra
   ├─ Đúng thông tin: Confirm → Contract ACTIVE
   ├─ Cần sửa: Request changes → Owner chỉnh và gửi lại
   └─ Sai người/không chấp nhận: Reject → Contract REJECTED
→ Khi ACTIVE, Tenant được xem Contract và layout riêng ở chế độ read-only
→ Nếu có Subscription ACTIVE, Tenant mới được chỉnh layout và sử dụng các chức năng WMS hậu thuê
```

“Trao đổi trực tiếp” và “ký hợp đồng giấy” chỉ được giải thích trên UI; không tạo status tương ứng trong database.

## 3. Quyết định về Booking

### 3.1 Quyết định

Bỏ Booking khỏi luồng nghiệp vụ mới.

Lý do:

- Không còn thanh toán cọc nên Booking không tạo ra cam kết tài chính hoặc quyền sử dụng kho.
- Bài đăng cần tiếp tục nhận liên hệ từ nhiều khách hàng tiềm năng.
- Booking hiện tại khóa một Warehouse và chuyển nó sang `RENTED`, không phù hợp với mô hình nhiều Tenant có thể cùng thuê một kho.
- Booking có thể bị spam và tạo trạng thái “đã giữ chỗ” sai với thỏa thuận thực tế ngoài hệ thống.
- Contract là bằng chứng nghiệp vụ để Tenant được gắn với Warehouse; Subscription mới là điều kiện mở khóa các thao tác quản lý hậu thuê.

### 3.2 Thay đổi trên giao diện

- Bỏ nút `Book now`, modal thanh toán cọc và thông báo “deposit deducted”.
- Bỏ trang `My Bookings` của Tenant.
- Bỏ danh sách approve/reject Booking của Owner.
- Bỏ số liệu Booking khỏi dashboard hoặc đổi sang số Contract nếu có ý nghĩa.
- Thay CTA bằng `View owner contact` hoặc `Contact owner`.
- Chỉ tài khoản đã đăng nhập mới xem đầy đủ số điện thoại Owner.
- Guest thấy lời nhắc đăng nhập, không nhận số điện thoại đầy đủ từ API.
- Có thể dùng liên kết `tel:` và nút sao chép số điện thoại; không cần tạo record Inquiry trong tuần này.

### 3.3 Xử lý code và dữ liệu Booking cũ

Không xóa bảng hoặc dữ liệu Booking ngay trong migration đầu tiên.

Thực hiện theo hai giai đoạn:

1. Ngừng tạo Booking mới, bỏ các endpoint khỏi FE và đánh dấu API Booking là deprecated.
2. Sau khi Contract đã dùng quan hệ trực tiếp, giữ bảng Booking cũ ở chế độ lịch sử trong một release; chỉ xóa ở migration sau nếu xác nhận không còn code/query nào sử dụng.

Không xóa enum `ApprovalStatus` dùng chung một cách máy móc vì Receipt và Withdrawal hiện cũng đang sử dụng enum này.

## 4. Mô hình Warehouse và diện tích thuê

### 4.1 Không quản lý vùng thuê vật lý dùng chung

Trong phạm vi hiện tại, không thêm `RentableArea`, `Zone` hoặc thuật toán phát hiện vùng chồng lấn.

Hệ thống cho phép:

- Một Warehouse có nhiều Contract với nhiều Tenant khác nhau cùng lúc.
- Hai Contract của hai Tenant khác nhau có thể cùng khai báo toàn bộ kích thước kho nếu hợp đồng giấy và thỏa thuận thực tế cho phép.
- Mỗi Tenant có một layout clone độc lập; thay đổi rack/bin của Tenant A không ảnh hưởng Tenant B hoặc layout mặc định của Owner.

Đây là chủ ý nghiệp vụ: StockSpace ghi nhận phạm vi sử dụng do hai bên khai báo, không xác nhận phân bổ mặt bằng vật lý giữa các Tenant.

### 4.2 Validation vẫn bắt buộc

Layout đề xuất cho Tenant phải thỏa mãn:

- `width > 0`, `length > 0`, `height > 0`.
- `width <= defaultLayout.width`.
- `length <= defaultLayout.length`.
- `height <= defaultLayout.height`.
- Rack nằm trong layout; Bin nằm trong Rack theo các validation geometry hiện có.
- `width`, `length`, `height` của tenant layout trở thành kích thước cố định sau khi Contract `ACTIVE`.
- Tenant được thêm/sửa/xóa rack và bin nhưng không được thay đổi kích thước tổng của tenant layout.

Không chỉ kiểm tra `leasedArea <= totalArea`, vì một hình chữ nhật dài 20 m × rộng 5 m vẫn không thể nằm trong mặt bằng 10 m × 10 m dù cùng diện tích.

### 4.3 Một giới hạn cần có

Cho phép nhiều Tenant cùng có Contract trên một Warehouse, nhưng không cho cùng một cặp `tenantId + warehouseId` có nhiều Contract hiệu lực chồng thời gian.

Lý do kỹ thuật:

- Tenant chỉ có một layout clone trên một Warehouse.
- Khi một Contract hết hạn, hệ thống hiện archive layout, clear stock và revoke staff theo cặp Tenant–Warehouse.
- Nếu cùng cặp Tenant–Warehouse có hai Contract đang hiệu lực, việc hết hạn một Contract có thể xóa dữ liệu trong khi Contract còn lại vẫn ACTIVE.

BE phải kiểm tra khoảng ngày của Contract mới không chồng với Contract `PENDING_TENANT_CONFIRM` hoặc `ACTIVE` khác của cùng Tenant–Warehouse. Các Tenant khác nhau không bị kiểm tra chồng lấn.

## 5. Trạng thái Contract mới

Trạng thái đề xuất:

```text
DRAFT
PENDING_TENANT_CONFIRM
CHANGES_REQUESTED
ACTIVE
REJECTED
EXPIRED
```

Chuyển trạng thái:

```text
Owner creates contract              → DRAFT
Owner submits contract              → PENDING_TENANT_CONFIRM
Tenant requests correction          → CHANGES_REQUESTED
Owner edits and resubmits            → PENDING_TENANT_CONFIRM
Tenant confirms                     → ACTIVE
Tenant rejects before activation    → REJECTED
Contract reaches end date           → EXPIRED
```

Quy tắc:

- Owner chỉ được sửa Contract khi `DRAFT` hoặc `CHANGES_REQUESTED`.
- Owner không được đổi `tenantId`, `warehouseId` sau lần submit đầu tiên. Nếu chọn sai, hủy bản nháp và tạo Contract mới.
- Tenant chỉ được confirm/request changes/reject khi `PENDING_TENANT_CONFIRM`.
- Contract `ACTIVE`, `REJECTED`, `EXPIRED` không được sửa nội dung.
- Không triển khai cancel/renewal/dispute trong đợt refactor này.
- `CHANGES_REQUESTED` phải lưu lý do mới nhất và thời điểm phản hồi.
- `REJECTED` phải lưu lý do; trường hợp “Owner gửi sai người” là một lý do hợp lệ.

Không tái sử dụng `DISPUTED` cho việc yêu cầu sửa thông tin. Đây là hai nghiệp vụ khác nhau.

## 6. Dữ liệu Contract cần lưu

`RentalContract` phải liên kết trực tiếp, không đi xuyên qua Booking:

- `owner_id`.
- `tenant_id`.
- `warehouse_id`.
- `tenant_layout_id` hoặc bản snapshot layout được chọn theo thiết kế triển khai.
- `status`.
- `start_date`, `end_date`.
- `rental_price_snapshot`: giá thuê được snapshot từ Warehouse listing; nullable với `NEGOTIATED`.
- `final_monthly_rent`: tổng giá thuê cuối cùng mỗi tháng.
- `pricing_type`: `FIXED_MONTHLY`, `PER_SQUARE_METER_MONTHLY`, `NEGOTIATED`.
- `leased_width`, `leased_length`, `leased_height`.
- `leased_area_m2` do BE tính bằng `leased_width × leased_length`, không tin giá trị FE gửi.
- `paper_contract_files`.
- `owner_note` nếu cần.
- `change_request_reason`, `rejection_reason`.
- `submitted_at`, `confirmed_at`, `expiry_reminder_sent`.

Giá và kích thước trong Contract là snapshot của thỏa thuận. Thay đổi giá bài đăng hoặc default layout sau đó không được sửa ngược Contract đã ACTIVE.

`booking_id` chuyển thành nullable trong giai đoạn tương thích, sau đó mới xóa khi dữ liệu đã backfill và không còn query phụ thuộc.

## 7. Cách xử lý layout trong Contract

### 7.1 Phương án triển khai trong tuần

Tận dụng `WarehouseLayout`, `WarehouseRack`, `WarehouseBin` hiện có:

1. Owner tạo Contract `DRAFT` và chọn Tenant bằng email hoặc ID.
2. BE xác nhận tài khoản có role Tenant và Warehouse thuộc Owner hiện tại.
3. BE tạo tenant layout draft từ default layout của Owner.
4. Owner dùng editor hiện tại để chỉnh kích thước, rack và bin qua API theo Contract.
5. Tenant xem layout ở chế độ read-only khi Contract được gửi.
6. Khi Tenant confirm, Contract thành `ACTIVE`; layout draft trở thành layout riêng của Tenant và Tenant được xem ở chế độ read-only.
7. Khi Tenant đồng thời có Subscription `ACTIVE`, Tenant mới được chỉnh rack/bin và dùng WMS; kích thước tổng vẫn không được thay đổi.

Không mở quyền Owner sửa trực tiếp mọi tenant layout qua endpoint chung. Tạo endpoint theo Contract và kiểm tra Contract thuộc Owner để tránh sửa nhầm layout của một Tenant đang hoạt động.

### 7.2 Trường hợp Tenant đã từng thuê kho này

- Nếu layout cũ đã archive do Contract trước hết hạn, khi tạo hợp đồng mới phải refresh layout từ default layout hiện tại của Owner theo quy tắc expiry đã thống nhất.
- Không tự khôi phục rack/bin cũ của Tenant nếu yêu cầu hiện tại là “thuê lại thì lấy mặt bằng mới từ Owner”.
- SKU, Category và lịch sử Receipt/Audit vẫn là dữ liệu của Tenant; không phụ thuộc layout mới.

## 8. Warehouse status sau refactor

Status sử dụng cho vòng đời bài đăng/kho:

```text
PENDING_APPROVAL
AVAILABLE
INACTIVE
```

`AVAILABLE` có nghĩa là bài đăng/kho đã được duyệt và có thể hiển thị, không có nghĩa “không có Tenant nào đang thuê”.

Không chuyển Warehouse sang `RENTED` khi tạo hoặc kích hoạt Contract.

Migration an toàn:

1. Chuyển dữ liệu `RENTED` cũ về `AVAILABLE` nếu Warehouse vẫn hoạt động và đã được duyệt.
2. Sửa toàn bộ query, dashboard, chatbot, inspection và layout logic đang phụ thuộc `RENTED`.
3. Chỉ xóa enum `RENTED` sau khi `rg` và test xác nhận không còn reference.

Số Tenant đang thuê hoặc occupancy phải tính từ số Contract `ACTIVE`, không suy ra từ Warehouse status.

### 8.1 Phân biệt ba loại giá

Team phải dùng thuật ngữ riêng trên API, code và UI để không nhầm ba khoản tiền:

1. **Giá gói đăng bài (`listingPackagePrice`)**: số tiền Owner trả cho StockSpace để bài được hiển thị trong 10, 15 hoặc 30 ngày.
2. **Giá thuê trên bài đăng (`rentalPrice`)**: giá Owner công bố khi tạo Warehouse listing để Tenant tham khảo.
3. **Giá thuê cuối cùng trong Contract (`finalMonthlyRent`)**: tổng tiền thuê mỗi tháng được tính hoặc nhập theo pricing type và được snapshot khi Owner gửi Contract.

Giá thuê trên Warehouse listing hỗ trợ ba cách:

- `FIXED_MONTHLY`: `rentalPrice` là tổng giá thuê toàn kho mỗi tháng.
- `PER_SQUARE_METER_MONTHLY`: `rentalPrice` là đơn giá thuê mỗi m² mỗi tháng.
- `NEGOTIATED`: bài đăng không có giá cố định; hai bên tự thỏa thuận ngoài hệ thống.

Quy tắc tính Contract:

| Pricing type | Phạm vi | Cách xác định `finalMonthlyRent` |
|---|---|---|
| `FIXED_MONTHLY` | Thuê nguyên kho | Bằng `rentalPrice` được snapshot từ bài đăng |
| `PER_SQUARE_METER_MONTHLY` | Thuê theo diện tích | `rentalPrice × leasedAreaM2` |
| `NEGOTIATED` | Theo thỏa thuận giấy | Owner bắt buộc nhập thủ công khi tạo/gửi Contract |

Trong đó:

- `leasedAreaM2 = leasedWidth × leasedLength`, do BE tính bằng `BigDecimal`.
- Với `FIXED_MONTHLY`, kích thước layout trong Contract phải bằng kích thước default layout của toàn kho; nếu chỉ thuê một phần thì phải dùng `PER_SQUARE_METER_MONTHLY` hoặc `NEGOTIATED`.
- Với `FIXED_MONTHLY` và `PER_SQUARE_METER_MONTHLY`, FE chỉ hiển thị preview; Owner không được sửa trực tiếp `finalMonthlyRent`.
- Với `NEGOTIATED`, `finalMonthlyRent` phải lớn hơn 0 và do Owner nhập sao cho khớp hợp đồng giấy.
- BE là nơi tính và validate giá cuối cùng; không tin số tổng do FE gửi.
- Contract snapshot cả `pricingType`, `rentalPrice`, `leasedAreaM2` và `finalMonthlyRent` để sau này giải thích được cách tính.
- Owner sửa giá bài đăng sau khi Contract đã gửi hoặc ACTIVE không được làm thay đổi Contract cũ.

Gói đăng bài mà Owner mua:

- Chỉ có combo 10, 15 hoặc 30 ngày theo quyết định của team.
- Mỗi combo có giá riêng; combo dài ngày có thể có đơn giá/ngày thấp hơn.
- Nên cấu hình bằng dữ liệu quản trị thay vì hardcode trên FE.
- Owner thanh toán bằng Wallet hiện có; đây là listing fee, không phải rental deposit.
- Lưu `publishedAt` và `visibleUntil` cho bài đăng.
- Gia hạn khi bài còn hạn: cộng ngày từ `visibleUntil`; khi đã hết hạn: tính từ thời điểm thanh toán thành công.
- Hết hạn hiển thị chỉ làm bài biến mất khỏi public search. Warehouse, Contract và quyền quan sát từ Contract ACTIVE không bị xóa; quyền hậu thuê vẫn được quyết định độc lập bởi Subscription của Tenant.
- Owner vẫn được tiếp tục tạo/gửi Contract cho contact đã có sau khi bài hết hạn, miễn Warehouse còn được Admin duyệt và không `INACTIVE`.

Không dùng Warehouse `AVAILABLE/INACTIVE` để thay thế trạng thái thời hạn của bài đăng. Nếu triển khai listing fee trong tuần này, nên có lifecycle riêng cho publication, ví dụ `DRAFT`, `PUBLISHED`, `EXPIRED`, thay vì gắn thêm nghĩa vào Warehouse status.

### 8.2 Ma trận quyền Contract và Subscription

Hai điều kiện độc lập:

- Contract trả lời: “Tenant có quan hệ thuê hợp lệ với Warehouse nào?”.
- Subscription trả lời: “Tenant được dùng các chức năng hậu thuê đến mức nào?”.

| Contract | Subscription | Quyền |
|---|---|---|
| Chưa ACTIVE | Bất kỳ | Chỉ xem Contract đang chờ và layout proposal read-only; không có quyền vận hành kho |
| ACTIVE | Không có/hết hạn | Xem Contract, Warehouse được thuê và tenant layout read-only; hiện CTA mua gói dịch vụ |
| ACTIVE | ACTIVE | Chỉnh rack/bin trong giới hạn layout; sử dụng Product/SKU, Stock, Inbound/Outbound, Audit, Staff và các chức năng WMS được hỗ trợ |
| EXPIRED/REJECTED | Bất kỳ | Không được vận hành kho; chỉ xem lịch sử Contract và dữ liệu lịch sử theo policy |

BE phải kiểm tra cả hai điều kiện trên mọi API mutation hậu thuê. Đặc biệt, `saveLayoutBulk` hiện mới kiểm tra Contract ACTIVE nên phải bổ sung kiểm tra Subscription ACTIVE. Các API Product, Stock, Receipt và Audit đã có kiểm tra subscription nhưng vẫn phải regression test để bảo đảm đồng nhất.

## 9. API đề xuất cho BE

Tên endpoint có thể điều chỉnh theo convention hiện tại, nhưng trách nhiệm phải giữ nguyên.

### 9.1 Contact Owner

```http
GET /api/warehouses/{warehouseId}/owner-contact
Authorization: Bearer <token>
```

Quy tắc:

- Chỉ Registered User đã đăng nhập.
- Warehouse phải được duyệt và không `INACTIVE`.
- Response tối thiểu: `ownerName`, `phone`.
- Public `GET /api/warehouses/{id}` không trả số điện thoại đầy đủ cho Guest.

### 9.2 Owner quản lý Contract

```http
POST /api/owner/contracts
GET  /api/contracts
GET  /api/contracts/{id}
PUT  /api/owner/contracts/{id}
PUT  /api/owner/contracts/{id}/layout
POST /api/owner/contracts/{id}/submit
```

Create request tối thiểu:

```json
{
  "warehouseId": "uuid",
  "tenantEmail": "tenant@example.com",
  "startDate": "2026-09-01",
  "endDate": "2027-08-31",
  "pricingType": "NEGOTIATED",
  "negotiatedMonthlyRent": 12000000,
  "leasedWidth": 10,
  "leasedLength": 8,
  "leasedHeight": 4,
  "paperContractFiles": ["https://..."]
}
```

BE tự tính `leasedAreaM2`. Với giá cố định hoặc theo m², BE cũng tự tính `finalMonthlyRent`; chỉ nhận `negotiatedMonthlyRent` khi pricing type là `NEGOTIATED`.

### 9.3 Tenant phản hồi Contract

```http
POST /api/tenant/contracts/{id}/confirm
POST /api/tenant/contracts/{id}/request-changes
POST /api/tenant/contracts/{id}/reject
```

Request changes/reject cần body:

```json
{
  "reason": "The leased dimensions do not match the signed paper contract."
}
```

### 9.4 Response Contract

Response phải đủ để FE không suy đoán nghiệp vụ:

- IDs và tên Owner, Tenant, Warehouse.
- Status và danh sách action được phép, hoặc các boolean `canEdit`, `canSubmit`, `canConfirm`, `canRequestChanges`, `canReject`.
- Ngày thuê, pricing type, giá thuê snapshot, diện tích và giá thuê cuối cùng mỗi tháng.
- Kích thước và diện tích do BE tính.
- File hợp đồng giấy.
- Layout ID hoặc layout tree read-only.
- Lý do yêu cầu sửa/từ chối.
- Timestamps.

FE nên ưu tiên action flags từ BE để tránh tự hardcode sai trạng thái.

## 10. Ảnh hưởng BE phải rà soát

### 10.1 Contract và Booking

- Tạo Contract trực tiếp thay cho `createContractFromBooking`.
- Thay mọi `contract.getBooking().getTenant/Warehouse()` bằng quan hệ trực tiếp.
- Sửa repository query danh sách Contract, kiểm tra quyền và tìm Warehouse ACTIVE của Tenant.
- Bỏ transfer/refund rental deposit khi confirm/reject/cancel.
- Bỏ `depositAmount` khỏi Contract response mới.
- Giữ tương thích đọc Contract cũ trong thời gian migration nếu production có dữ liệu.

### 10.2 Quyền quan sát và quyền sử dụng WMS

Contract ACTIVE chỉ cấp quyền xem Warehouse/Contract/tenant layout. Các service hậu thuê phải kiểm tra đồng thời Contract ACTIVE và Subscription ACTIVE:

- Warehouse/Layout access.
- Stock Batch.
- Inventory Receipt.
- Inventory Audit.
- Staff assignment.
- Active warehouse context của Chatbot.
- Danh sách kho Tenant đang thuê.

Tất cả query phải chuyển sang `contract.tenant`, `contract.warehouse`; không được còn điều kiện `contract.booking...`.

Các endpoint read-only dùng để xem Contract và tenant layout không được bắt Tenant mua Subscription. Ngược lại, mọi endpoint tạo/sửa/xóa dữ liệu layout hoặc WMS phải trả `SUBSCRIPTION_REQUIRED` khi gói không tồn tại hoặc đã hết hạn.

### 10.3 Expiry scheduler

Khi Contract hết hạn:

- Chuyển `ACTIVE → EXPIRED`.
- Gửi reminder trước 30 ngày theo cấu hình hiện có.
- Clear tồn kho vận hành của đúng Tenant–Warehouse theo policy đã chốt.
- Archive tenant layout.
- Revoke staff assignment trên Warehouse đó.
- Không đổi Warehouse sang `AVAILABLE`, vì Warehouse đã luôn AVAILABLE theo nghĩa bài đăng.
- Trước khi cleanup, kiểm tra không còn Contract ACTIVE khác của cùng Tenant–Warehouse. Validation không chồng thời gian là lớp bảo vệ đầu; check lúc chạy scheduler là lớp bảo vệ cuối.

### 10.4 Dispute và rental deposit

- Bỏ Dispute khỏi luồng thuê kho chính và khỏi FE.
- Không dùng Admin để phân xử hợp đồng giấy hoặc tiền cọc.
- Không xóa Wallet top-up, Subscription payment, Listing fee hoặc Withdrawal.
- Phân biệt rõ từ “deposit” nạp tiền vào ví với “rental security deposit”; chỉ bỏ rental security deposit.
- Giữ transaction history cũ để audit; không rewrite lịch sử giao dịch production.

### 10.5 Thành phần phụ

Rà và sửa:

- Admin/Owner stats đang đếm Booking hoặc Warehouse `RENTED`.
- Chatbot tools về Booking và occupancy.
- Notification template chứa Booking/deposit.
- System config `deposit_percentage`.
- RBAC permissions và seed data của Booking/Dispute.
- OpenAPI/Swagger.
- Unit/integration tests.

## 11. Ảnh hưởng FE phải triển khai

### FE 1 — Public listing và contact

- Warehouse Detail: bỏ booking card và deposit modal.
- Thêm CTA xem/copy/gọi Owner contact; Guest phải đăng nhập.
- Bỏ nhãn `Instant Booking`, `Rented` và nội dung rental guarantee không còn đúng.
- Không ẩn bài đăng chỉ vì có Contract ACTIVE.
- Bỏ My Bookings khỏi route/sidebar/store/API client.
- Rà Landing page, footer và chatbot suggested prompts còn chữ Booking.

### FE 2 — Contract và layout proposal

- Owner Contracts: thêm create draft, chọn Tenant, nhập dates/price/dimensions, upload hợp đồng giấy, mở layout editor và submit.
- Tenant Contracts: view đầy đủ, view layout read-only, confirm/request changes/reject.
- Hiển thị status mới và chỉ action theo flags BE trả về.
- Bỏ toàn bộ deposit, cancel-deal và dispute UI khỏi Contract.
- Khi Contract ACTIVE nhưng chưa có Subscription, điều hướng Tenant tới layout read-only và CTA mua gói.
- Khi cả Contract và Subscription ACTIVE, mở các màn hình chỉnh layout và WMS.

### Quy tắc chung cho FE

- Không tự tính quyền truy cập chỉ từ Warehouse status.
- Không tự cho phép action chỉ vì nút đang hiện; BE vẫn là nguồn validation cuối.
- Hiển thị lỗi BE nguyên nghĩa và có fallback thân thiện.
- Sau khi mutation thành công, refetch Contract thay vì tự đoán status tiếp theo.

## 12. Phân công BE

Hai BE nên chia theo ranh giới để giảm conflict.

### BE A — Contract core và migration

- Migration thêm direct relations/snapshot fields/status mới.
- Backfill Contract cũ từ Booking.
- Contract entity/repository/service/controller/DTO.
- Contact Owner endpoint.
- Status transition và overlap validation.
- Unit/integration test Contract.

### BE B — Dependency cleanup và WMS access

- Chuyển query WMS/Staff/Layout/Chatbot khỏi `contract.booking` và áp dụng đủ hai lớp Contract + Subscription.
- Warehouse status migration và cleanup `RENTED`.
- Expiry scheduler.
- Stats, notifications, RBAC, seed/config cleanup.
- Regression test quyền Tenant trên Warehouse.

Nếu chỉ có một BE triển khai, vẫn giữ thứ tự commit ở mục 13; không sửa tất cả trong một commit.

## 13. Thứ tự commit đề xuất

1. `refactor(contract): add direct tenant and warehouse references`
   - Add nullable columns, backfill, dual-read hoặc chuyển query sau khi backfill.
   - Chưa xóa Booking.

2. `feat(contract): support owner-created rental contract drafts`
   - Create/update/submit, snapshot giá và kích thước, upload references.

3. `feat(contract): add tenant contract review actions`
   - Confirm, request changes, reject và state validation.

4. `feat(layout): support contract-scoped tenant layout proposals`
   - Clone/edit/view proposal, geometry validation và khóa dimensions sau activation.

5. `refactor(wms): authorize tenant warehouse access through direct contracts`
   - Stock, Receipt, Audit, Staff, Layout, Chatbot context.

6. `refactor(warehouse): decouple listing availability from rental contracts`
   - Ngừng `markAsRented`, migrate RENTED → AVAILABLE, stats/search/inspection updates.

7. `refactor(booking): retire booking and rental deposit flows`
   - Disable writes/endpoints, remove rental-deposit operations và dependent config.

8. `refactor(contract): simplify expiry and remove dispute flow`
   - EXPIRED cleanup, remove cancel/dispute from active API surface.

9. `docs(api): add FE contract migration guide`
   - Endpoint examples, status/action matrix, FE checklist.

10. FE commits tách theo màn hình:
    - `refactor(warehouse): replace booking with owner contact`
    - `feat(owner): add direct contract creation flow`
    - `feat(tenant): add contract review flow`
    - `refactor(contract): remove deposit and dispute UI`

Không merge FE dùng API mới trước khi BE migration/API tương ứng đã deploy trên môi trường integration.

## 14. Kế hoạch thực hiện trong tuần

### Ngày 1 — Chốt contract API và migration

- Chốt field, status và action matrix.
- BE tạo migration direct relations và backfill.
- FE tạo types/API mocks theo response đã chốt.
- Chạy query kiểm tra Contract/Booking dữ liệu hiện có.

### Ngày 2 — Contract direct flow

- BE hoàn thành create/update/submit và tenant review actions.
- FE 1 hoàn thành contact Owner và loại bỏ entry point Booking.
- FE 2 dựng Owner/Tenant Contract screens.

### Ngày 3 — Layout proposal và hai lớp quyền Contract/Subscription

- BE hoàn thành contract-scoped layout.
- Chuyển toàn bộ query access khỏi Booking.
- FE tích hợp layout editor/read-only preview.

### Ngày 4 — Cleanup và regression

- Warehouse không còn bị khóa `RENTED`.
- Expiry, Staff, Stock, Receipt, Audit, Chatbot và notification được regression test.
- FE bỏ Booking/deposit/dispute text, route và store còn sót.

### Ngày 5 — Integration và demo rehearsal

- Chạy E2E happy path và negative cases.
- Kiểm tra migration trên bản sao dữ liệu trước production.
- Cập nhật diagrams/report/use cases bị ảnh hưởng.
- Chốt API guide và demo script.

## 15. Tiêu chí nghiệm thu

### Happy path

1. Guest xem bài đăng nhưng không thấy đầy đủ số điện thoại.
2. Tenant đăng nhập và lấy được contact Owner.
3. Owner tạo Contract cho đúng Tenant, nhập giá/ngày/kích thước và file hợp đồng.
4. Owner chỉnh layout proposal hợp lệ và submit.
5. Tenant xem đúng thông tin và confirm.
6. Contract chuyển `ACTIVE`; Tenant xem được layout riêng nhưng chưa có Subscription thì không sửa được.
7. Tenant mua gói dịch vụ và sau đó chỉnh rack/bin nhưng không đổi dimensions.
8. Khi cả hai điều kiện đều ACTIVE, Tenant sử dụng Receipt/Stock/Audit và assign Staff bình thường.
9. Bài đăng vẫn hiển thị cho người khác; Warehouse không chuyển `RENTED`.
10. Owner có thể tạo Contract cho Tenant khác trên cùng Warehouse.

### Negative cases

- Guest gọi owner-contact API → `401/403`.
- Owner tạo Contract cho user không phải Tenant → reject.
- Owner dùng Warehouse của người khác → reject.
- Contract thiếu file giấy, ngày sai hoặc duration dưới minimum → reject.
- Dimensions vượt default layout → reject.
- Rack/Bin vượt parent geometry → reject.
- Owner sửa Contract sau submit/active → reject theo status.
- Tenant không thuộc Contract gọi confirm → reject.
- Tenant confirm hai lần → reject idempotently hoặc trả state hiện tại theo convention đã chốt.
- Tenant request changes → Owner sửa và resubmit được.
- Tenant reject → Contract không cấp quyền WMS.
- Contract ACTIVE nhưng không có Subscription → xem được layout, mọi mutation hậu thuê bị chặn bằng `SUBSCRIPTION_REQUIRED`.
- Subscription ACTIVE nhưng không có Contract ACTIVE trên Warehouse → không được xem tenant layout hoặc vận hành Warehouse đó.
- Cùng Tenant–Warehouse tạo Contract có thời gian chồng lấn → reject.
- Tenant khác tạo Contract cùng Warehouse → được phép.
- Contract hết hạn → quyền WMS bị thu hồi và cleanup đúng một lần.

### Regression bắt buộc

- Public search/list/detail Warehouse.
- Owner post/update Warehouse và Admin approve.
- Subscription/listing fee/wallet top-up/withdrawal.
- Tenant layout, SKU, inbound/outbound, stock, audit.
- Staff assignment và revoke khi Contract hết hạn.
- Contract expiry reminder email.

## 16. Migration và rollback

Trước deploy:

- Đếm Contract không có Booking hợp lệ.
- Đếm Booking có nhiều Contract bất thường.
- Kiểm tra owner/tenant/warehouse có thể backfill đầy đủ.
- Kiểm tra các Warehouse `RENTED` và trạng thái phê duyệt tương ứng.
- Kiểm tra các cặp Tenant–Warehouse có nhiều Contract ACTIVE.

Migration phải:

- Có preflight read-only.
- Add column trước, backfill sau, đặt constraint cuối.
- Không xóa bảng/column Booking trong cùng release.
- Không xóa transaction history rental deposit cũ.
- Có post-deploy verification query.

Rollback ứng dụng phải còn đọc được Contract backfill. Không rollback database bằng cách xóa dữ liệu Contract mới đã phát sinh.

## 17. Tài liệu và diagram phải cập nhật sau code

- Use case: bỏ Booking, deposit, rental dispute; thêm Create Contract, Submit Contract, Request Changes, Reject Contract.
- Class diagram Contract: quan hệ trực tiếp Owner/Tenant/Warehouse/Layout.
- Contract sequence diagram: bắt đầu từ Owner tạo Contract sau trao đổi ngoài hệ thống.
- Contract state machine: dùng action trên mũi tên và status mới.
- Booking Management section trong report: xóa hoặc đổi thành Contract Initiation/Marketplace Contact tùy cấu trúc báo cáo được phép.
- Warehouse state diagram: bỏ nghĩa `RENTED`.
- Context/use-case/package documentation và chatbot descriptions còn Booking/deposit.

Chỉ cập nhật tài liệu sau khi code và API cuối cùng đã được merge để tiếp tục giữ nguyên quy tắc “diagram đồng bộ code thực tế”.

## 18. Kết luận nghiệp vụ

Hướng này thực tế và vừa scope nếu nhóm mô tả đúng vai trò của hệ thống:

- Bài đăng tạo cơ hội liên hệ, không giữ chỗ.
- Thỏa thuận và ký giấy diễn ra ngoài hệ thống.
- Digital Contract ghi nhận thỏa thuận và cấp quyền quan sát Warehouse/layout riêng.
- Subscription còn hiệu lực mới mở khóa quyền chỉnh layout và các chức năng quản lý hậu thuê.
- Mỗi Tenant có môi trường layout/WMS độc lập khi đáp ứng đủ hai điều kiện.
- Nền tảng không cam kết phát hiện chồng lấn diện tích giữa các Tenant.

Các nền tảng quản lý thuê thực tế cũng hỗ trợ upload hợp đồng đã ký bên ngoài để chia sẻ/lưu trữ và cho phép gửi bản sửa trước khi hoàn tất; đây là cơ sở phù hợp cho cách StockSpace lưu bản số hóa thay vì tự xử lý toàn bộ thương lượng và thanh toán thuê.

Tham khảo:

- TenantCloud — upload signed lease and share it with tenant: https://support.tenantcloud.com/en/articles/11941623-how-do-i-upload-my-own-lease
- Zillow Rental Manager — upload an existing or revised lease: https://zillow.zendesk.com/hc/en-us/articles/33025938267795-uploading-an-existing-lease
- WhichWarehouse — warehouse requirement/contact and tailored quotation model: https://www.whichwarehouse.com/quote.html
