# StockSpace - Contract Expiry & Subscription Integration Guide

Tài liệu này dùng cho FE và thành viên phụ trách cấu hình email sau hai thay đổi backend:

- `c140b71 fix(subscription): enforce normalized staff limits`
- `c60ced4 feat(contract): automate rental expiry handling`
- `26f32ce fix(subscription): remove unsupported package quotas`
- `972e734 fix(subscription): hide inactive packages from public flows`
- `4d0e984 feat(subscription): expire overdue subscriptions`
- `23667b0 fix(subscription): preserve package duration snapshot`

Phạm vi hiện tại là xử lý cơ bản khi hợp đồng hết hạn. Chưa triển khai gia hạn hoặc hủy hợp đồng sau khi hợp đồng đã chuyển sang `ACTIVE`.

## 1. Thay đổi backend

### 1.1 Subscription package

Backend đã chuẩn hóa giới hạn nhân viên bằng field riêng:

```json
{
  "maxStaff": 2
}
```

`features.max_staff` và `features.max_products` không còn được sử dụng làm quota. Khi tạo hoặc chỉnh sửa package, Admin phải gửi `maxStaff` ở top-level request.

Các field package được lưu gồm:

- `name`
- `features`
- `price`
- `durationDays`
- `maxStaff`

Khi Tenant mua package, backend lưu snapshot của tên, giá, features và maxStaff tại thời điểm mua. Vì vậy việc Admin chỉnh sửa package sau đó không làm thay đổi trực tiếp quyền lợi của subscription đã mua.

Lưu ý: `features` chỉ là metadata mô tả quyền lợi để hiển thị. Backend chưa có bộ kiểm tra tự động cho các feature flag bên trong JSON. Hiện tại giới hạn `maxStaff` và việc Tenant có subscription còn hiệu lực được kiểm tra rõ trong các luồng liên quan. Không nên giả định rằng thêm một key mới vào `features` sẽ tự động bật hoặc giới hạn một nghiệp vụ mới.

`max_products` đã được loại bỏ khỏi package metadata vì hệ thống không giới hạn số lượng SKU theo package. `max_staff` cũng không nên lặp lại trong `features` vì đã có field `maxStaff` riêng.

### 1.2 Không cho downgrade trong thời gian package còn hiệu lực

Nếu Tenant đang có package ACTIVE, backend sẽ từ chối package mới khi:

- Giá package mới thấp hơn package hiện tại; hoặc
- `maxStaff` của package mới thấp hơn giới hạn hiện tại.

API preview trả về `canProceed: false` và `transactionType: "DOWNGRADE_BLOCKED"` trong trường hợp này. API purchase cũng kiểm tra lại ở backend, vì FE không được chỉ dựa vào kết quả preview.

Các transaction type hiện có:

| `transactionType` | Ý nghĩa | FE xử lý |
|---|---|---|
| `NEW_PURCHASE` | Tenant chưa có package active | Cho phép mua |
| `RENEWAL` | Mua lại đúng package hiện tại | Cho phép gia hạn |
| `UPGRADE` | Package mới không thấp hơn về giá và giới hạn staff | Cho phép nâng cấp |
| `DOWNGRADE_BLOCKED` | Package mới thấp hơn về giá hoặc giới hạn staff | Không cho submit purchase |

### 1.3 Public package và package inactive

Public API chỉ trả các package có `isActive = true` và chưa bị soft-delete:

```http
GET /api/packages
GET /api/packages/{id}
```

Package đã bị Admin ngừng cung cấp sẽ không xuất hiện ở hai API này. Backend cũng kiểm tra lại trạng thái package trong preview và purchase; FE không được chỉ dựa vào danh sách package đã load trước đó.

Package nội bộ như posting fee vẫn cần được loại khỏi trang subscription bằng metadata `features.type = "POSTING_FEE"`; package này không phải gói subscription dành cho Tenant.

### 1.4 Reminder trước khi hợp đồng hết hạn

Scheduler backend chạy mỗi ngày lúc `00:00` theo timezone của server.

Khoảng một tháng trước ngày hết hạn, backend sẽ gửi reminder cho:

- Tenant;
- Warehouse Owner.

Reminder gồm:

- Email;
- In-app notification với type `RENTAL`.

Mỗi contract chỉ gửi reminder một lần thông qua field `expiryReminderSent`.

### 1.5 Xử lý khi hợp đồng hết hạn

Khi contract `ACTIVE` đã quá `endDate`, scheduler sẽ:

1. Soft-delete các stock batch đang active của warehouse.
2. Giữ lại SKU và category.
3. Giữ lại receipt, inventory transaction và dữ liệu lịch sử.
4. Revoke toàn bộ staff assignment ACTIVE của Tenant tại warehouse đó.
5. Soft-delete tenant layout cùng các rack/bin thuộc layout đó.
6. Chuyển contract sang `COMPLETED`.
7. Chuyển warehouse về `AVAILABLE`.
8. Gửi notification cho Tenant và Owner.

Dữ liệu không bị xóa vật lý khỏi database. Các API đọc dữ liệu hiện tại sẽ không còn trả stock batch đã soft-delete.

### 1.6 Khi Tenant thuê lại warehouse

Khi contract mới được tạo và backend gọi lại quy trình clone layout:

- Tenant layout cũ được khôi phục;
- Kích thước layout được lấy lại từ owner default layout hiện tại;
- Rack/bin cũ của tenant layout được soft-delete;
- Rack/bin mới được clone lại từ owner default layout hiện tại.

Như vậy Tenant không tiếp tục dùng layout cũ nếu Owner đã thay đổi default layout trong thời gian warehouse được trả về trạng thái `AVAILABLE`.

## 2. FE cần làm gì

### 2.1 Subscription package

Khi hiển thị package cho Tenant:

- Dùng `maxStaff` ở top-level response;
- Không tự lấy `features.max_staff` để thay thế `maxStaff`;
- Hiển thị đúng `price`, `durationDays`, `features` từ response;
- Dùng `maxStaff` để hiển thị giới hạn staff, không đọc lại `max_staff` từ `features`.

FE không được render nguyên chuỗi JSON `features`. Nên parse JSON nếu hợp lệ và chuyển thành các dòng/chip dễ đọc, ví dụ `wms: true` thành `WMS access`. Không hiển thị các key nội bộ như `max_staff` hoặc `max_products`. Nếu Admin nhập mô tả text thông thường thay vì JSON, hiển thị như một mô tả đơn giản.

Trước khi mua hoặc đổi package:

```http
GET /api/tenant/subscriptions/preview-change?packageId={packageId}
```

Nếu response có:

```json
{
  "canProceed": false,
  "transactionType": "DOWNGRADE_BLOCKED"
}
```

FE cần disable nút xác nhận và hiển thị `message` từ backend.

Nếu preview hoặc purchase trả lỗi package inactive, FE cần đóng/disable flow mua và refresh lại danh sách package.

Khi `canProceed: true`, FE có thể gọi:

```http
POST /api/tenant/subscriptions
```

Backend vẫn kiểm tra lại downgrade ở bước purchase.

### 2.2 Khi subscription hết hạn

Backend có scheduler chạy mỗi ngày lúc `00:05` theo timezone server. Các subscription ACTIVE có `endDate` đã qua sẽ được chuyển thành:

- `status: "EXPIRED"`;
- `isActive: false`.

FE không cần tự cập nhật database. Khi `/api/tenant/subscriptions/active` trả `404`/`SUBSCRIPTION_NOT_FOUND`, hiển thị trạng thái không có subscription active và hướng Tenant đến trang package.

`durationDays` trong subscription response là snapshot tại thời điểm mua/gia hạn/nâng cấp, không bị thay đổi nếu Admin sửa duration của package về sau.

### 2.3 Khi contract hết hạn

Không cần thêm API mới cho phạm vi này. FE cần bảo đảm:

- Sau khi nhận notification `RENTAL`, refresh danh sách warehouse và contract;
- Không hiển thị warehouse đã chuyển sang `AVAILABLE` như warehouse đang thuê của Tenant;
- Refresh stock overview/stock list sau khi contract hết hạn;
- Hiển thị trạng thái không còn quyền truy cập nếu API trả `403`;
- Refresh staff assignment list để không còn hiển thị assignment đã bị revoke;
- Hiển thị notification/email reminder nếu UI đã có phần notification tương ứng.

FE không cần tự xóa SKU, category hoặc history. Các dữ liệu này vẫn được backend giữ lại.

### 2.4 Layout

FE không cần tự xử lý việc archive hoặc clone lại layout. FE chỉ cần:

- Gọi API layout như hiện tại;
- Refresh layout sau khi Tenant thuê lại warehouse;
- Không cache layout cũ quá lâu;
- Khi Tenant không còn active contract, không cố gọi layout API như thể warehouse vẫn đang được thuê.

## 3. Người phụ trách email cần làm gì

Backend đã implement phần gửi email và các scheduler. Người phụ trách email không cần viết thêm code, nhưng cần kiểm tra cấu hình môi trường deploy.

### 3.1 Required environment variables

```env
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_16_char_app_password
MAIL_FROM=StockSpace <your_email@gmail.com>
FRONTEND_URL=https://your-frontend-domain
```

Backend hiện dùng Gmail SMTP:

- Host: `smtp.gmail.com`
- Port: `587`
- STARTTLS: enabled

Nếu dùng Gmail, `MAIL_PASSWORD` phải là App Password 16 ký tự của tài khoản đã bật 2-Step Verification, không dùng mật khẩu Gmail thông thường.

### 3.2 Checklist sau deploy

1. Kiểm tra container/backend đã nhận đúng các biến môi trường.
2. Kiểm tra log không có lỗi SMTP authentication.
3. Kiểm tra email người gửi trong `MAIL_FROM`.
4. Kiểm tra server đang chạy liên tục để scheduler có thể chạy lúc `00:00`.
5. Kiểm tra contract sắp hết hạn trong database/test environment và xác nhận email đến Tenant và Owner.
6. Kiểm tra notification `RENTAL` được tạo bình thường.

Nếu SMTP chưa cấu hình, các thay đổi contract vẫn được xử lý; chỉ phần email reminder không gửi được. Vì vậy SMTP phải được cấu hình trước khi cần kiểm thử reminder thực tế.

Email reminder được gửi bất đồng bộ. Sau khi deploy cần theo dõi log gửi email thực tế; lỗi SMTP không làm dừng cleanup contract, nhưng reminder của contract đó không có cơ chế retry tự động trong phạm vi hiện tại.

## 4. Migration cần có trong deployment

Chạy các migration trong thư mục `ops/migrations` trước hoặc trong bước deploy backend:

- `20260816_fix_service_package_staff_limits.sql`
- `20260816_contract_expiry_cleanup.sql`
- `20260816_cleanup_package_feature_metadata.sql`
- `20260816_subscription_package_snapshot_duration.sql`

Migration đầu tiên bổ sung/backfill `service_packages.max_staff` và sửa snapshot staff limit của subscription cũ.

Migration thứ hai bổ sung `rental_contracts.expiry_reminder_sent` để tránh gửi reminder lặp lại.

Migration thứ ba dọn các key quota cũ `max_staff` và `max_products` khỏi JSON metadata hợp lệ. Migration thứ tư bổ sung và backfill `subscriptions.snapshot_duration_days`.

## 5. Phạm vi chưa thực hiện

Các nội dung sau chưa nằm trong phạm vi hiện tại:

- Gia hạn contract rental sau khi đã `ACTIVE`;
- Hủy contract sớm sau khi đã `ACTIVE`;
- Quy trình hoàn/trừ tiền cọc khi hủy sớm;
- Luồng handover hoặc settlement phức tạp;
- Tự động xóa vật lý dữ liệu.

FE không nên hiển thị hoặc bật các nút sau khi backend chưa có API tương ứng:

- `Cancel Subscription`;
- `Upgrade Plan` nếu chưa nối vào preview/purchase flow;
- Các thao tác gia hạn/hủy contract ACTIVE.

Không nên tự thêm các nút hoặc gọi API cho các luồng trên nếu backend chưa cung cấp nghiệp vụ tương ứng.
