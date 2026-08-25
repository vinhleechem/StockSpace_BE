# Backend Commit Checklist — Rental Contract Refactor sau Review 3

> Tài liệu triển khai chi tiết cho hai Backend: **Phần A — VIỆT ANH thực hiện trước**, sau đó **Phần B — VINH thực hiện**.
>
> Plan nghiệp vụ nguồn: `review3_rental_contract_refactor_plan.md` trong cùng thư mục Obsidian.

## 0. Cách sử dụng checklist

### Quy tắc bắt buộc

- Mỗi checkbox dưới đây tương ứng đúng **một nhiệm vụ và một commit**.
- Không gộp hai checkbox vào một commit; không chia một checkbox thành nhiều commit nếu chưa cập nhật lại checklist và thông báo cho người còn lại.
- Thực hiện đúng thứ tự từ trên xuống. Phần B chỉ bắt đầu sau khi toàn bộ Phần A đã được check, push và VINH đã lấy đúng commit bàn giao.
- Mỗi commit phải build được. Test mới hoặc test bị ảnh hưởng phải nằm trong chính commit đó, không dồn toàn bộ test về cuối.
- Không merge/deploy các commit trung gian lên production. Chỉ deploy sau Gate cuối vì trong giai đoạn chuyển tiếp API Contract mới đã tồn tại nhưng một số WMS/scheduler cũ vẫn còn phụ thuộc Booking.
- Không sửa FE trong checklist này.
- Không xóa dữ liệu lịch sử Booking, Dispute hoặc Transaction trong migration đầu. Chỉ ngừng phát sinh dữ liệu mới và tách application code khỏi chúng.
- Không xóa các giá trị `DEPOSIT_*` và `COMMISSION` khỏi `TransactionType` nếu database còn transaction lịch sử dùng các giá trị đó.
- Không xóa `ApprovalStatus`, vì Receipt và Withdrawal vẫn đang dùng enum này.
- Mọi migration mới đặt trong `ops/migrations`, chạy lặp lại an toàn khi có thể và có preflight/post-check tương ứng.
- Không dùng `double` cho tiền hoặc kích thước; dùng `BigDecimal` và phép nhân/so sánh rõ ràng.
- Không tin `finalMonthlyRent`, `leasedAreaM2`, action flags hoặc quyền truy cập do FE gửi.
- Sau mỗi commit ghi hash vào dòng `Commit hoàn thành` và đổi `[ ]` thành `[x]`.

### Lệnh kiểm tra tối thiểu cho mỗi commit

```powershell
.\mvnw.cmd -q -DskipTests compile
.\mvnw.cmd -q -Dtest=<TargetTest1>,<TargetTest2> test
git diff --check HEAD~1 HEAD
git status --short
```

Commit cuối của mỗi người phải chạy:

```powershell
.\mvnw.cmd verify
```

### Quy ước nghiệp vụ không được tự thay đổi

- Warehouse là bài đăng được duyệt; không bị khóa chỉ vì có người thuê.
- Nhiều Tenant được có Contract ACTIVE trên cùng Warehouse.
- Cùng một `tenantId + warehouseId` không được có Contract `PENDING_TENANT_CONFIRM` hoặc `ACTIVE` chồng ngày.
- Không quản lý Zone/RentableArea và không kiểm tra chồng lấn vật lý giữa các Tenant.
- `Contract ACTIVE` cấp quyền xem Contract, Warehouse thuê và tenant layout read-only.
- `Contract ACTIVE + Subscription ACTIVE` mới cấp quyền chỉnh layout và thao tác WMS hậu thuê.
- `FIXED_MONTHLY`: thuê nguyên kho, `finalMonthlyRent = rentalPriceSnapshot`.
- `PER_SQUARE_METER_MONTHLY`: `finalMonthlyRent = rentalPriceSnapshot × leasedAreaM2`.
- `NEGOTIATED`: Owner nhập `negotiatedMonthlyRent`; BE validate và lưu thành `finalMonthlyRent`.
- Owner/Tenant/Warehouse của Contract bất biến từ lúc tạo Draft. Chọn sai thì xóa mềm Draft và tạo Contract mới.
- Contract lưu `layoutSnapshot` lúc submit/resubmit; tenant layout vận hành có thể thay đổi sau ACTIVE nhưng snapshot hợp đồng không đổi.

### Baseline code đã xác nhận trước khi chia việc

Checklist này được đối chiếu sau khi chạy `git fetch origin dev` ngày 2026-08-25:

- Local `HEAD` và `origin/dev` cùng ở commit `4759d59cc5a140ddcf77b8348c7d05e75f8184a5`.
- Working tree sạch tại thời điểm lập checklist.
- `RentalContract.booking` hiện là quan hệ bắt buộc; `ContractService`, `DisputeService` và `ContractExpiryScheduler` đang lấy Tenant/Warehouse qua `getBooking()`.
- `Warehouse` và API hiện dùng `pricePerMonth`; `WarehouseService.createWarehouse` đang trừ flat publish fee qua system config/ServicePackage.
- `WarehouseLayoutService.saveLayoutBulk` kiểm tra Contract ACTIVE nhưng chưa kiểm tra Subscription khi Tenant sửa layout.
- Product, Stock, Receipt và Audit hiện có các subscription check rời rạc; chưa có access policy tập trung kết hợp Contract + Subscription.
- Booking approval vẫn gọi `markAsRented`; WarehouseService/Layout/Inspection/Stats/Chatbot còn reference `RENTED`.

Nếu `origin/dev` thay đổi sau baseline này, người bắt đầu A1 phải fetch lại, ghi HEAD mới và rà lại các search gate trước khi code. Không được giả định file/method vẫn giống baseline.

### Phân bổ khối lượng sau khi cân lại

| Người thực hiện | Functional commits | Gate test commit | Trọng tâm |
|---|---:|---:|---|
| VIỆT ANH | 11 | 1 | Pricing, Contract core, layout proposal, contact, Listing/Publication và access-policy nền tảng |
| VINH | 9 | 1 | Chuyển các consumer, expiry, retire legacy, schema final và regression cuối |

Số commit là 12–10 và phần của VIỆT ANH chứa nhiều thay đổi nền tảng hơn. VINH không phải tự thiết kế lại model/API; VINH nhận API/access service đã ổn định để chuyển từng consumer và cleanup. Đây là phân chia theo dependency code, không chia đều máy móc theo số file.

### Convention commit/code

- Dùng Conventional Commit có scope giống các commit gần đây của repository, ví dụ `feat(contract): ...`, `refactor(wms): ...`, `fix(subscription): ...`.
- Không commit format toàn project, generated files, `target/`, IDE settings hoặc thay đổi ngoài nhiệm vụ checkbox.
- Tên Java giữ convention hiện tại: entity số ít, DTO có hậu tố Request/Response, repository/service/controller theo module package.
- Endpoint dùng danh từ số nhiều và role prefix hiện có (`/api/owner`, `/api/tenant`, `/api/admin`) khi action chỉ dành cho một actor.
- Migration dùng prefix ngày và số thứ tự như checklist; không sửa migration đã chạy ở bất kỳ environment nào, phải tạo migration kế tiếp.
- Mỗi validation nghiệp vụ phải nằm ở service/validator BE; controller chỉ nhận auth/input và map response.
- Mỗi commit phải cập nhật test cùng thay đổi, không dùng việc “VINH sẽ test sau” để bỏ test ở Phần A.

---
# PHẦN A — VIỆT ANH THỰC HIỆN TRƯỚC

## A0. Điều kiện bắt đầu (Gate, không phải nhiệm vụ commit)

- **Xác nhận branch làm việc sạch và ghi baseline trước refactor.**
  - **Thực hiện:**
    - Checkout branch mới từ `origin/dev`, đề xuất `refactor/rental-contract-core`.
    - Chạy `git status --short --branch` và lưu commit baseline vào phần Handoff cuối tài liệu.
    - Chạy `./mvnw.cmd verify` trước khi sửa code.
    - Chạy read-only SQL để đếm: Contract theo status, Contract thiếu Booking hợp lệ, cặp Tenant–Warehouse có nhiều ACTIVE Contract, Warehouse `RENTED`, Booking/Dispute/Deposit transaction hiện có.
  - **Hoàn thành khi:** baseline build xanh và có kết quả preflight để đối chiếu sau migration.
  - **Baseline commit:** `________________`

## A1. Rental pricing rõ nghĩa trên Warehouse

- [x] Thêm mô hình giá thuê `FIXED_MONTHLY`, `PER_SQUARE_METER_MONTHLY`, `NEGOTIATED` cho Warehouse
  - **Commit đề xuất:** `refactor(warehouse): model rental pricing explicitly`
  - **Migration:** `ops/migrations/20260825_01_add_warehouse_rental_pricing.sql`.
  - **Code chính:**
    - Thêm enum `warehouse/entity/RentalPricingType.java`.
    - `Warehouse`: thêm `rentalPricingType`, đổi semantic `pricePerMonth` thành `rentalPrice`.
    - `CreateWarehouseRequest`, `UpdateWarehouseRequest`, `WarehouseResponse`, `WarehouseSearchRequest`.
    - `WarehouseService`, `WarehouseRepository`, `PublicWarehouseController` và các chatbot warehouse DTO/map có liên quan.
  - **Migration chi tiết:**
    - Thêm `rental_pricing_type VARCHAR(40)` và backfill `FIXED_MONTHLY` cho dữ liệu cũ.
    - Thêm `rental_price NUMERIC(15,2)` và backfill từ `price_per_month`.
    - Trong commit này chưa drop `price_per_month`; đánh dấu là cột chuyển tiếp để VINH xóa ở migration cuối.
    - Thêm check: pricing type thuộc ba giá trị hợp lệ; rental price `> 0` với FIXED/PER_M2 và `NULL` với NEGOTIATED.
  - **Validation API:**
    - `FIXED_MONTHLY` và `PER_SQUARE_METER_MONTHLY`: `rentalPrice` bắt buộc, `> 0`, tối đa 2 chữ số thập phân.
    - `NEGOTIATED`: bỏ qua hoặc reject `rentalPrice`; response trả `null`.
    - Giữ alias đọc `pricePerMonth` chỉ nếu cần tương thích ngắn hạn; OpenAPI mới chỉ quảng bá `rentalPrice`.
  - **Test trong commit:** mở rộng `WarehouseServiceTest`, test repository/search và chatbot mapping bị ảnh hưởng.
  - **Không làm:** chưa triển khai gói đăng bài 10/15/30 ngày; chưa sửa `RENTED`.
  - **Hoàn thành khi:** create/update/search/detail Warehouse trả pricing type và rental price đúng cả ba mode.
  - **Commit hoàn thành:** `3b5a7ff`

## A2. Schema Contract additive và backfill quan hệ trực tiếp

- [x] Thêm direct relations, snapshot fields và status chuyển tiếp cho RentalContract
  - **Commit đề xuất:** `refactor(contract): add direct rental relations and snapshots`
  - **Migration:** `ops/migrations/20260825_02_add_direct_rental_contract_fields.sql`.
  - **Entity/enum:**
    - `RentalContract` liên kết trực tiếp `owner`, `tenant`, `warehouse`.
    - Thêm `pricingType`, `rentalPriceSnapshot`, `finalMonthlyRent`.
    - Thêm `leasedWidth`, `leasedLength`, `leasedHeight`, `leasedAreaM2`.
    - Thêm `layoutSnapshot` kiểu TEXT, `changeRequestReason`, `rejectionReason`, `confirmedAt`.
    - Đổi Java property `paperContractImages` thành `paperContractFiles`; migration rename column nếu an toàn.
    - `paperContractFiles` và `layoutSnapshot` phải lưu JSON hợp lệ qua `ObjectMapper`; tuyệt đối không dùng `List.toString()`.
    - `booking` chuyển nullable trong entity để hỗ trợ Contract mới không có Booking.
  - **ContractStatus chuyển tiếp:** enum và DB constraint tạm thời phải chấp nhận cả status cũ lẫn mới:
    - Mới: `DRAFT`, `PENDING_TENANT_CONFIRM`, `CHANGES_REQUESTED`, `ACTIVE`, `REJECTED`, `EXPIRED`.
    - Cũ tạm giữ: `UNDER_NEGOTIATION`, `PENDING_TERMINATION`, `PENDING_CANCEL`, `CANCELLED`, `PENDING_HANDOVER`, `COMPLETED`, `DISPUTED`.
  - **Backfill:**
    - `owner_id = booking.warehouse.owner_id`.
    - `tenant_id = booking.tenant_id`.
    - `warehouse_id = booking.warehouse_id`.
    - Contract cũ có Warehouse pricing cũ: snapshot `FIXED_MONTHLY`, rental price và final rent từ giá Warehouse.
    - Kích thước cũ lấy từ tenant layout đang active; nếu không có thì default layout; nếu vẫn thiếu phải được preflight báo ra, không tự ghi số giả.
  - **Database:** direct FK vẫn nullable trong giai đoạn chuyển tiếp; thêm index cho `owner_id`, `tenant_id`, `warehouse_id`, `(tenant_id, warehouse_id, status)`.
  - **Compatibility helper:** tạo các accessor nội bộ ưu tiên direct relation, fallback Booking chỉ cho legacy Contract; code mới không được tạo Booking.
  - **Test trong commit:** tạo `RentalContractMigrationCompatibilityTest` hoặc service-level mapping test cho Contract direct và legacy.
  - **Hoàn thành khi:** ứng dụng đọc được cả Contract cũ và Contract direct; build không yêu cầu Booking non-null.
  - **Commit hoàn thành:** `9cc9465`

## A3. Owner tạo và xóa Draft Contract trực tiếp

- [x] Xây API Owner tạo Draft Contract không qua Booking
  - **Commit đề xuất:** `feat(contract): support owner-created contract drafts`
  - **API cuối:**
    - `POST /api/owner/contracts/preview` để tính thử dimensions/area/final rent, không ghi database.
    - `POST /api/owner/contracts`.
    - `DELETE /api/owner/contracts/{contractId}` chỉ soft-delete `DRAFT`.
    - `GET /api/contracts` và `GET /api/contracts/{id}` đọc direct relation trước.
  - **DTO mới:** `CreateRentalContractRequest`, `RentalContractResponse` mới, mapper riêng nếu cần.
  - **Create request:** `warehouseId`, `tenantEmail`, `startDate`, `endDate`, `leasedWidth`, `leasedLength`, `leasedHeight`, `negotiatedMonthlyRent` chỉ khi Warehouse là NEGOTIATED, `paperContractFiles`, `ownerNote`.
  - **Validation identity/quyền:**
    - Current user phải là Owner và sở hữu Warehouse.
    - Warehouse phải verified, không deleted và không `INACTIVE`; không yêu cầu bài đăng còn hạn để tạo Contract cho contact cũ.
    - Tìm Tenant bằng email không phân biệt hoa thường; user phải active, không deleted và có `ROLE_TENANT`.
    - Không trả danh sách Tenant toàn hệ thống cho Owner; chỉ exact lookup thông qua create request.
    - `ownerId`, `tenantId`, `warehouseId` bất biến từ lúc Draft được tạo.
  - **Validation ngày:** start/end bắt buộc, start không sau end, duration tối thiểu theo policy hiện hành; không chấp nhận Contract đã hết hạn tại thời điểm tạo.
  - **Validation giá/kích thước:**
    - Tính `leasedAreaM2 = width × length`.
    - Dimensions dương và không vượt từng chiều default layout.
    - FIXED yêu cầu dimensions đúng bằng default layout và snapshot giá Warehouse.
    - PER_M2 snapshot đơn giá Warehouse và BE tính final rent.
    - NEGOTIATED yêu cầu giá nhập thủ công `> 0`; không dùng giá từ FE ở hai mode còn lại.
    - Tất cả snapshot được ghi ngay khi tạo Draft; thay đổi bài đăng sau đó không đổi Draft.
  - **Preview response:** trả pricing type, rental price hiện tại, leased dimensions, area và final monthly rent; create vẫn tính lại toàn bộ để chống request giả hoặc giá vừa thay đổi.
  - **Khởi tạo layout:**
    - FIXED/full-size: refresh tenant layout từ default, gồm racks/bins.
    - Partial: tạo/refresh tenant layout với dimensions Contract và layout rỗng để Owner setup.
    - Nếu có layout archived từ lần thuê cũ: soft-delete racks/bins cũ, refresh cùng layout row; không tạo row trùng cho cùng Tenant–Warehouse.
  - **Overlap:** ở bước Draft chưa khóa overlap; overlap phải được kiểm tra bắt buộc tại submit. Có thể cảnh báo sớm ở create.
  - **Soft delete Draft:** archive layout proposal nếu không có Contract ACTIVE khác của cùng Tenant–Warehouse.
  - **Test trong commit:** create happy cases cho ba pricing modes; owner sai; tenant role sai; dimensions sai; delete Draft; Contract không có Booking.
  - **Hoàn thành khi:** Owner tạo được Draft direct và Contract response không cần booking/deposit.
  - **Commit hoàn thành:** `c430893`

## A4. Layout proposal theo Contract

- [x] Thêm API Owner chỉnh và Tenant xem layout proposal theo Contract
  - **Commit đề xuất:** `feat(layout): support contract-scoped layout proposals`
  - **API cuối:**
    - `GET /api/owner/contracts/{contractId}/layout`.
    - `PUT /api/owner/contracts/{contractId}/layout`.
    - `GET /api/tenant/contracts/{contractId}/layout`.
  - **Owner rules:**
    - Chỉ Owner của Contract được GET/PUT.
    - PUT chỉ khi Contract `DRAFT` hoặc `CHANGES_REQUESTED`.
    - Request dimensions phải đúng dimensions snapshot của Contract; Owner chỉ được sửa positions/racks/bins.
    - Dùng toàn bộ geometry/capacity validation hiện có: rack trong layout, bin trong rack, rotation, code uniqueness, max volume/weight.
  - **Tenant rules:**
    - Tenant của Contract được GET khi status `PENDING_TENANT_CONFIRM`, `CHANGES_REQUESTED`, `ACTIVE`, `REJECTED` hoặc `EXPIRED` theo policy read-only.
    - Tenant không được PUT qua contract layout endpoint.
    - Không yêu cầu Subscription cho GET read-only.
  - **Implementation:**
    - Tách logic bulk-save dùng chung khỏi controller-role string để ContractService gọi bằng quyền đã xác thực.
    - Không mở quyền Owner sửa tenant layout tùy ý qua `/api/owner/warehouses/{id}/layout`.
    - Thêm serializer ổn định cho layout tree để dùng làm snapshot; tránh phụ thuộc thứ tự repository không xác định.
  - **Test trong commit:** owner edit hợp lệ/sai status; tenant read-only; người ngoài bị 403; geometry invalid; dimensions bị thay đổi.
  - **Hoàn thành khi:** Owner hoàn thiện nội thất proposal trước submit và Tenant xem được đúng proposal nhưng không sửa.
  - **Commit hoàn thành:** `0a222e8`

## A5. Owner cập nhật và submit Contract

- [x] Hoàn thiện state transition `DRAFT/CHANGES_REQUESTED → PENDING_TENANT_CONFIRM`
  - **Commit đề xuất:** `feat(contract): support contract editing and submission`
  - **API cuối:**
    - `PUT /api/owner/contracts/{contractId}`.
    - `POST /api/owner/contracts/{contractId}/submit`.
  - **Update rules:**
    - Chỉ sửa terms, dates, price theo mode, dimensions/layout khi `DRAFT` hoặc `CHANGES_REQUESTED`.
    - Không sửa owner/tenant/warehouse. Nếu sai recipient dùng DELETE Draft hoặc Tenant reject sau submit.
    - Nếu đổi dimensions trước submit, validate lại và reset/validate layout proposal để không còn rack/bin nằm ngoài bounds.
    - Với CHANGES_REQUESTED, giữ reason cũ trong audit/log cho đến khi resubmit; response cho FE biết lần sửa đang xử lý.
  - **Submit transaction:**
    - Lock Warehouse row bằng pessimistic write hoặc cơ chế tương đương để hai submit đồng thời không vượt overlap rule.
    - Query overlap cho cùng Tenant–Warehouse với status `PENDING_TENANT_CONFIRM` hoặc `ACTIVE`, date range inclusive; bỏ qua chính Contract hiện tại.
    - Tenant khác trên cùng Warehouse được phép.
    - Validate lại toàn bộ terms, paper files, layout geometry và pricing tại server.
    - Serialize layout hiện tại vào `layoutSnapshot`.
    - Set `PENDING_TENANT_CONFIRM`, `submittedAt = now`, clear change-request marker đang xử lý.
    - Gửi notification cho đúng Tenant; lỗi notification không được rollback Contract nếu convention hiện tại dùng best-effort.
  - **Action flags response:** `canEdit`, `canDelete`, `canSubmit`, `canConfirm`, `canRequestChanges`, `canReject`, `canViewLayout`, `canManageWms` phải do BE tính theo user/status/subscription.
  - **Test trong commit:** submit/resubmit; overlap inclusive; concurrent/lock path; khác Tenant được phép; thiếu paper file; invalid layout; immutable direct IDs; snapshot không đổi khi default layout đổi.
  - **Hoàn thành khi:** FE Owner có đủ API từ tạo Draft đến gửi Contract mà không gọi Booking API.
  - **Commit hoàn thành:** `d75c479`

## A6. Tenant review và kích hoạt Contract

- [x] Thêm Confirm, Request Changes và Reject theo state machine mới
  - **Commit đề xuất:** `feat(contract): add tenant contract review actions`
  - **API cuối:**
    - `POST /api/tenant/contracts/{contractId}/confirm`.
    - `POST /api/tenant/contracts/{contractId}/request-changes` body `{ "reason": "..." }`.
    - `POST /api/tenant/contracts/{contractId}/reject` body `{ "reason": "..." }`.
  - **Common rules:**
    - Chỉ Tenant direct của Contract.
    - Chỉ gọi từ `PENDING_TENANT_CONFIRM`.
    - Reason trim, bắt buộc, giới hạn độ dài cho request-changes/reject.
    - Action lặp lại trả lỗi nghiệp vụ nhất quán; không tạo transaction/deposit/refund.
  - **Confirm:**
    - Validate lại ngày chưa hết hạn và overlap.
    - Set `ACTIVE`, `confirmedAt = now`; không đổi Warehouse sang `RENTED`.
    - Không clone layout lần nữa; proposal hiện tại trở thành tenant layout.
    - Không cấp quyền mutation WMS nếu Subscription không ACTIVE; response `canManageWms=false`.
  - **Request changes:** set `CHANGES_REQUESTED`, lưu reason, notify Owner; Owner sau đó được edit/resubmit.
  - **Reject:** set `REJECTED`, lưu reason, archive proposal nếu không có quyền thuê ACTIVE khác; không ảnh hưởng bài đăng hoặc Tenant khác.
  - **Response/list/detail:** direct relations, pricing snapshots, files, layout snapshot/ID, reasons, timestamps, action flags; bỏ deposit khỏi contract API mới.
  - **Test trong commit:** ba action happy path; wrong tenant/status; no subscription vẫn confirm được nhưng WMS locked; confirm không mark RENTED; no wallet interaction.
  - **Hoàn thành khi:** toàn bộ Contract lifecycle trước ACTIVE chạy độc lập Booking/Deposit/Dispute.
  - **Commit hoàn thành:** `cbbcb69`

## A7. Contact Owner có xác thực và ngăn lộ phone cho Guest

- [x] Thay public owner phone bằng endpoint contact yêu cầu đăng nhập
  - **Commit đề xuất:** `feat(warehouse): expose authenticated owner contact`
  - **API cuối:** `GET /api/warehouses/{warehouseId}/owner-contact`.
  - **Response:** `warehouseId`, `ownerId`, `ownerName`, `phone`; không trả email nếu team chưa chốt công khai email.
  - **Rules:**
    - Registered User đã login; Warehouse verified, active, not deleted, không INACTIVE.
    - Guest gọi endpoint nhận 401/403 theo SecurityConfig hiện tại.
    - `GET /api/warehouses` và `GET /api/warehouses/{id}` không trả phone đầy đủ cho Guest.
    - Không tạo Inquiry/Booking record khi xem contact.
  - **Code:** tạo DTO riêng, service method, controller method; sửa `WarehouseResponse`/mapper để không leak `ownerPhone` public.
  - **Test trong commit:** anonymous denied; logged-in allowed; inactive/unverified/not-found; public response không chứa phone.
  - **Hoàn thành khi:** FE có thể thay nút Booking bằng Contact Owner mà không lộ phone công khai.
  - **Commit hoàn thành:** `1e0dbc6`

## A8. Catalog gói đăng bài 10/15/30 ngày

- [x] Tách Listing Package khỏi Tenant Service Package
  - **Commit đề xuất:** `feat(listing): add warehouse listing package catalog`
  - **Migration:** `ops/migrations/20260825_03_add_listing_packages.sql`.
  - **Model:** `ListingPackage` riêng với `name`, `durationDays`, `price`, `isActive`; không dùng `ServicePackage.features={type:POSTING_FEE}`.
  - **Seed:** đúng ba combo 10, 15, 30 ngày; giá seed theo quyết định team/config hiện tại, không hardcode ở FE.
  - **Public API:**
    - `GET /api/listing-packages` chỉ trả active/not-deleted.
    - `GET /api/listing-packages/{id}` từ chối inactive/deleted trong public flow.
  - **Admin API:** CRUD/activate/deactivate dưới `/api/admin/listing-packages`; duration chỉ chấp nhận 10/15/30 và unique.
  - **RBAC:** thêm permission riêng cho Admin listing package nếu cần; không dùng `PACKAGE_PURCHASE` của Tenant.
  - **Compatibility:** chưa xóa posting fee ServicePackage/config cũ trong commit này.
  - **Test trong commit:** public active filtering, admin validation, unique duration, inactive package.
  - **Hoàn thành khi:** Owner FE lấy được đúng ba combo từ API, Admin có thể đổi giá/active mà không ảnh hưởng WMS subscription.
  - **Commit hoàn thành:** `574124d`

## A9. Owner mua/gia hạn thời gian hiển thị bài đăng

- [x] Thêm Listing Order và publication period cho Warehouse
  - **Commit đề xuất:** `feat(listing): support paid warehouse publication periods`
  - **Migration:** `ops/migrations/20260825_04_add_warehouse_publications.sql`.
  - **Model tối thiểu:**
    - `ListingOrder`: owner, warehouse, listingPackage, duration snapshot, price snapshot, periodStart, periodEnd, transaction reference.
    - `Warehouse`: `publishedAt`, `visibleUntil`; publication status được tính `DRAFT/PUBLISHED/EXPIRED`, không cần thêm một status mơ hồ vào `WarehouseStatus`.
    - `Transaction`: thêm nullable `listingOrderId`; thêm `TransactionType.LISTING_FEE`, giữ legacy `COMMISSION`.
  - **API:** `POST /api/owner/warehouses/{warehouseId}/publications` body `{ "listingPackageId": "uuid" }`; `GET /api/owner/warehouses/{warehouseId}/publications`.
  - **Rules:**
    - Warehouse thuộc Owner, đã verified/approved, not deleted, không INACTIVE.
    - Package active và thuộc catalog listing.
    - Deduct wallet và tạo order trong cùng transaction; lỗi bất kỳ rollback cả hai.
    - Lần đầu: start `now`, end `now + duration`.
    - Gia hạn còn hạn: start bằng `visibleUntil`, end cộng duration.
    - Gia hạn đã hết: start `now`.
    - Giá/duration snapshot không đổi khi Admin sửa package sau giao dịch.
    - Idempotency/concurrency: lock Warehouse row khi mua để hai request đồng thời không ghi đè `visibleUntil`; nếu API có idempotency key thì reject duplicate key.
  - **Test trong commit:** first publish, renew active/expired, insufficient balance rollback, inactive package, wrong owner, concurrent extension.
  - **Hoàn thành khi:** listing fee có transaction/reference/history rõ ràng và thời hạn không bị mất ngày.
  - **Commit hoàn thành:** `cc40bb4` (corrective: `ee86472`)

## A10. Public search chỉ hiển thị bài còn hạn

- [x] Áp dụng publication visibility và bỏ flat posting fee cũ khi tạo Warehouse
  - **Commit đề xuất:** `refactor(listing): enforce publication visibility in public search`
  - **Code:** `WarehouseRepository`, `WarehouseService`, public search/detail, chatbot search/detail, Warehouse response.
  - **Rules:**
    - Public search chỉ trả Warehouse verified, AVAILABLE, active/not-deleted và `visibleUntil >= now`.
    - Public detail của bài hết hạn trả not-found hoặc unavailable theo convention, nhưng Owner/Admin vẫn xem được qua endpoint quản trị.
    - Contract ACTIVE và WMS không phụ thuộc `visibleUntil`.
    - Owner vẫn tạo Contract cho contact cũ khi publication hết hạn.
    - Bỏ logic trừ `warehouse_publish_fee`/posting `ServicePackage` trong `createWarehouse`; tạo Warehouse không tự trừ tiền.
    - Bỏ seed/config posting fee cũ sau khi migration xác nhận không còn code dùng; không xóa transaction COMMISSION lịch sử.
    - Response cho Owner có `publicationStatus`, `publishedAt`, `visibleUntil` và `canPublish/canRenew`.
  - **Migration cleanup:** có thể đặt trong `20260825_04...` nếu chưa deploy; nếu migration trước đã deploy thì tạo file `_05_cleanup_legacy_posting_fee_config.sql`, không sửa migration đã chạy.
  - **Test trong commit:** search active/expired/unpublished; owner view; contract unaffected; create warehouse no wallet deduction.
  - **Hoàn thành khi:** phí đăng bài và giá thuê Warehouse hoàn toàn tách biệt.
  - **Commit hoàn thành:** `f11f5e5` (corrective: `2143052`)

## A11. Access policy trung tâm cho Contract và Subscription

- [x] Tạo service quyền dùng chung và chuyển Layout/Tenant Warehouse sang direct Contract
  - **Commit đề xuất:** `refactor(access): centralize tenant warehouse authorization`
  - **Service đề xuất:** `TenantWarehouseAccessService` với các method rõ nghĩa:
    - `requireActiveContract(tenantId, warehouseId)`.
    - `canObserveWarehouse(tenantId, warehouseId)`.
    - `requireActiveSubscription(tenantId)`.
    - `requireWmsAccess(tenantId, warehouseId)` = cả hai điều kiện.
  - **Repository:** mọi query dùng `contract.tenant` và `contract.warehouse`, không `contract.booking`; chỉ tính active/not-deleted và ngày Contract còn hiệu lực.
  - **Layout:**
    - GET tenant layout chỉ cần Contract ACTIVE.
    - PUT tenant layout cần Contract ACTIVE + Subscription ACTIVE.
    - Bổ sung subscription check cho `saveLayoutBulk`, hiện đang thiếu.
    - Owner default layout không còn bị cấm chỉ vì Warehouse RENTED; việc xử lý thay đổi default và tenant snapshots phải độc lập.
  - **Tenant Warehouse:** danh sách my-warehouses từ direct ACTIVE Contract, distinct Warehouse.
  - **Error:** không Contract → FORBIDDEN/CONTRACT_REQUIRED theo convention; không Subscription → `SUBSCRIPTION_REQUIRED`.
  - **Test trong commit:** ma trận 4 trường hợp Contract/Subscription; layout GET/PUT; direct query không Booking.
  - **Hoàn thành khi:** có một nguồn quyền thống nhất để các WMS module gọi, không copy query/if khác nhau.
  - **Commit hoàn thành:** `f060ae7`

## A12. Gate bàn giao từ VIỆT ANH sang VINH

- [x] Chạy full verification và bàn giao Contract, Listing và Access core cho VINH
  - **Commit đề xuất:** `test(platform): cover rental core and listing lifecycle`
  - **Nội dung commit:**
    - Bổ sung integration/service tests còn thiếu xuyên suốt A1–A11, không thay đổi business API nếu không phải sửa bug test phát hiện.
    - Test E2E service-level: Owner tạo/approve/publish Warehouse → Tenant lấy contact → Owner create/setup/submit Contract → Tenant request changes → Owner resubmit → Tenant confirm.
    - Test pricing đủ ba mode, listing 10/15/30 ngày, overlap, nhiều Tenant cùng Warehouse, phone privacy và ma trận Contract/Subscription cho layout.
    - Test legacy Contract vẫn map được trong giai đoạn chuyển tiếp.
  - **Lệnh bắt buộc:** `.\mvnw.cmd verify`.
  - **Bàn giao:** push branch; ghi HEAD hash; VINH phải branch/pull từ đúng hash này, không lấy lại từ dev cũ.
  - **Lưu ý:** branch tại Gate A chưa được deploy production vì các WMS consumer, scheduler và legacy flow chưa cleanup hoàn toàn.
  - **Hoàn thành khi:** CI-equivalent verify xanh, working tree sạch, tất cả A1–A11 có hash.
  - **Commit hoàn thành / Handoff HEAD:** `f055a0e`

---

# PHẦN B — VINH THỰC HIỆN SAU KHI NHẬN HANDOFF

## B0. Điều kiện bắt đầu của VINH (Gate, không phải nhiệm vụ commit)

- **Xác nhận VINH đang làm trên đúng Handoff HEAD của A12.**
  - **Thực hiện:** `git rev-parse HEAD`, so với hash A12; `git status --short`; `.\mvnw.cmd verify`.
  - **Không được:** checkout từ dev cũ rồi cherry-pick tùy ý; sửa lại migration A1/A2/A8/A9 đã bàn giao mà không trao đổi.
  - **Handoff hash đã xác nhận:** `f055a0e` (đã xác nhận là ancestor của baseline `1bab796`)

## B1. Chuyển toàn bộ WMS khỏi Contract.booking

- [x] Áp dụng direct Contract + Subscription gate cho Product, Stock, Receipt và Audit
  - **Commit đề xuất:** `refactor(wms): authorize operations through direct contracts`
  - **Code bắt buộc rà:**
    - `StockBatchRepository`, `StockBatchService`, `StockBatchController`.
    - `InventoryReceiptService/Controller/Repository`.
    - `InventoryAuditService/Controller/Repository`.
    - `TenantProductController`, Product SKU/Category services nếu endpoint là mutation hậu thuê.
    - Warehouse stock overview và mọi native/JPQL query có Contract.
  - **Rules:**
    - Warehouse-scoped read/write tồn kho cần direct Contract ACTIVE; mutation cần Subscription ACTIVE.
    - Product/SKU/Category là dữ liệu Tenant được giữ qua Contract expiry nhưng create/update/delete vẫn cần Subscription ACTIVE theo plan; read history theo policy hiện có.
    - Staff action phải resolve đúng Tenant chủ quản trước khi gọi access service.
    - Không thêm lại dependency Booking chỉ để test pass.
  - **Search gate:** chạy `rg "contract\.booking|getBooking\(\)"` trên WMS; kết quả phải bằng 0.
  - **Test trong commit:** cập nhật `StockBatchServiceTest`, `InventoryReceiptServiceTest`, `InventoryAuditServiceTest`, Product tests; thêm active/no-subscription/no-contract cases.
  - **Hoàn thành khi:** Tenant dùng được toàn bộ WMS với Contract direct và không thể bypass Subscription qua endpoint còn sót.
  - **Commit hoàn thành:** `b976d29`

## B2. Staff authorization theo Contract direct

- [x] Chuyển Staff assignment/revoke sang access policy mới
  - **Commit đề xuất:** `refactor(staff): authorize assignments through direct contracts`
  - **Rules:**
    - Tenant assign Staff vào Warehouse phải có Contract ACTIVE với Warehouse và Subscription ACTIVE.
    - Staff phải là member active của đúng Tenant; không dùng role/user tenant khác.
    - Resignation, auto-revoke và work history không được phụ thuộc Booking.
    - Revoke theo đúng `tenantId + warehouseId`; không ảnh hưởng Staff assignment của Tenant khác trên cùng Warehouse.
    - Read work history được giữ sau Contract expiry; mutation mới bị khóa.
  - **Code:** `TenantStaffService`, assignment/member repositories, controllers và các helper resolve tenant.
  - **Test trong commit:** active/no-contract/no-subscription, wrong tenant, multiple tenants same Warehouse, revoke isolation, history retained.
  - **Hoàn thành khi:** Staff workflow không còn query qua Booking và không bypass Subscription.
  - **Commit hoàn thành:** `f4b4117`

## B3. Chatbot theo direct Contract và API mới

- [x] Đồng bộ Chatbot tools/context với Contract, Listing và pricing mới
  - **Commit đề xuất:** `refactor(chatbot): align tools with direct rental contracts`
  - **Context:** `ActiveWarehouseContextResolver` và tenant WMS tools dùng `TenantWarehouseAccessService`.
  - **Remove:** `GetWarehouseBookingsTool` khỏi registry, prompt và suggested operations.
  - **Contract tools:** `GetMyContractsTool`/`GetContractDetailTool` trả status, dimensions, pricing snapshot/final rent; không trả booking/deposit/dispute.
  - **Warehouse tools:** dùng `rentalPricingType`, `rentalPrice`, publication visibility; không lộ owner phone cho Guest.
  - **Occupancy:** nếu tool cần “số Tenant đang thuê”, tính từ direct ACTIVE Contracts; không suy ra Warehouse.RENTED.
  - **Knowledge/prompt:** bỏ câu Booking guarantee, rental deposit và Admin phân xử cọc.
  - **Test trong commit:** registry, active context, public/tenant tools, no phone leak, no booking tool.
  - **Hoàn thành khi:** Chatbot không quảng bá hoặc gọi API đã retire và tuân thủ cùng access policy như REST API.
  - **Commit hoàn thành:** `111670e`

## B4. Stats và notifications theo Contract mới

- [x] Thay booking/rented/deposit metrics bằng Contract/listing metrics rõ nghĩa
  - **Commit đề xuất:** `refactor(stats): report direct contract and listing metrics`
  - **Admin stats:** bỏ `totalBookings` hoặc đổi thành `totalContracts`/counts theo status; không đổi tên field mà giữ semantic cũ.
  - **Owner stats:** không dùng RENTED để tính occupancy/revenue; có thể trả activeContractCount và activeTenantCount distinct.
  - **Revenue:** rental deposit không còn là platform/owner revenue; listing fee và service package payment là các nguồn platform revenue tách loại.
  - **Notifications:** sửa type/message booking/deposit sang contract submit/change/confirm/reject và listing publish/expire; không tạo notification cũ mới.
  - **Test trong commit:** AdminStatsServiceTest, OwnerStatsServiceTest, NotificationServiceTest và mapper/controller response.
  - **Hoàn thành khi:** dashboard backend không còn số liệu có tên/ý nghĩa Booking hoặc Warehouse RENTED.
  - **Commit hoàn thành:** `912b8b0`

## B5. Expiry scheduler theo Contract direct

- [x] Đổi scheduler sang `ACTIVE → EXPIRED` và cleanup an toàn
  - **Commit đề xuất:** `refactor(contract): simplify expiry and tenant cleanup`
  - **Scheduler rules:**
    - Reminder trước 30 ngày, gửi một lần bằng `expiryReminderSent`; dùng direct Owner/Tenant/Warehouse.
    - Khi `endDate < today`: lock/process idempotently, set EXPIRED, cleanup đúng Tenant–Warehouse.
    - Clear current operational stock theo policy đã chốt; giữ SKU, Category, Receipt/Audit/Transaction history.
    - Archive tenant layout và racks/bins.
    - Revoke staff assignments của Warehouse đó.
    - Không đổi Warehouse status.
    - Trước cleanup kiểm tra không còn Contract ACTIVE khác của cùng Tenant–Warehouse; nếu có, log cảnh báo và không cleanup dữ liệu dùng chung.
    - Job chạy lại không double-clear/double-notify.
  - **Legacy mapping behavior:** scheduler không xử lý status cũ sau khi final migration chạy; trước final migration phải bỏ qua có log, không crash.
  - **Test trong commit:** reminder boundary, expiry, idempotency, no duplicate cleanup, active sibling contract safety, notification failure policy.
  - **Hoàn thành khi:** Contract expiry không cần Booking, deposit, handover hoặc markAsAvailable.
  - **Commit hoàn thành:** `f1f3b51`

## B6. Retire Booking và rental deposit khỏi application flow

- [x] Ngừng toàn bộ API Booking và nghiệp vụ rental security deposit
  - **Commit đề xuất:** `refactor(booking): retire rental requests and security deposits`
  - **Remove/disable application code:** Tenant/Owner Booking controllers, BookingService và chatbot booking tool; repository/entity có thể giữ tạm nếu migration compatibility còn cần ở commit này.
  - **Contract:** xóa `createContractFromBooking`, fallback Booking sau khi xác nhận direct fields đã đầy đủ; response không còn bookingId/depositAmount.
  - **Wallet:** không còn call `DEPOSIT_PAYMENT`, `DEPOSIT_RECEIVED`, `DEPOSIT_REFUND`; giữ enum values và transaction rows lịch sử để deserialize/audit.
  - **Config/seed:** bỏ `deposit_percentage` khỏi public/admin config và DataInitializer; không nhầm với Wallet top-up.
  - **RBAC:** bỏ `RENTAL_REQUEST_*`; giữ mapping migration an toàn cho permission rows cũ hoặc soft-delete chúng.
  - **Stats/notification:** không còn booking counters/templates.
  - **Database:** chưa drop `booking_requests` nếu cần giữ lịch sử; application không tạo/update row mới.
  - **Test trong commit:** booking endpoints absent/410 theo strategy đã chốt; no deposit wallet interaction; permission policy; existing TOP_UP, PACKAGE_PAYMENT, LISTING_FEE, WITHDRAWAL vẫn chạy.
  - **Hoàn thành khi:** `rg "BookingService|createContractFromBooking|deposit_percentage" src/main/java` không còn active business reference.
  - **Commit hoàn thành:** `fd3f295`

## B7. Retire Dispute/cancel/handover flow cũ

- [x] Bỏ Dispute và các Contract transition ngoài state machine mới
  - **Commit đề xuất:** `refactor(contract): retire dispute cancellation and handover flows`
  - **Remove/disable:** DisputeController/Service active API, tenant-report-failed, owner-cancel, tenant-respond-cancel, confirm-handover.
  - **ContractStatus code:** chỉ business code mới dùng `DRAFT`, `PENDING_TENANT_CONFIRM`, `CHANGES_REQUESTED`, `ACTIVE`, `REJECTED`, `EXPIRED`; legacy status còn trong enum tới migration B9 nếu cần đọc dữ liệu.
  - **RBAC:** bỏ `DISPUTE_*`, `CONTRACT_HANDOVER_CONFIRM`; cập nhật descriptions của owner/tenant contract permissions.
  - **Policy/notification/chatbot:** bỏ tuyên bố Admin/Inspector phân xử tiền cọc; không biến request-changes thành Dispute.
  - **Data:** giữ dispute table/evidence lịch sử read-only hoặc export theo quyết định dữ liệu; không hard-delete production rows trong commit code.
  - **Test trong commit:** security endpoints, state transition invalid, no dispute/deposit references trong Contract response.
  - **Hoàn thành khi:** application chỉ còn sáu Contract status mới trong mọi luồng tạo mới.
  - **Commit hoàn thành:** `60ddfa3`

## B8. Bỏ Warehouse.RENTED khỏi code

- [x] Decouple hoàn toàn trạng thái bài đăng khỏi Contract
  - **Commit đề xuất:** `refactor(warehouse): remove rented listing status`
  - **Code rà bắt buộc:** WarehouseService, WarehouseLayoutService, InspectionService, Repository search, Admin/Owner stats, Chatbot localization/search/occupancy, DTO comments/tests.
  - **Rules:**
    - WarehouseStatus chỉ `PENDING_APPROVAL`, `AVAILABLE`, `INACTIVE`.
    - Không còn `markAsRented` hoặc `markAsAvailable` do Contract action.
    - Owner sửa default layout không bị chặn bởi RENTED; Contract ACTIVE giữ tenant layout/snapshot riêng.
    - Admin approval/inspection vẫn chuyển PENDING_APPROVAL → AVAILABLE theo flow hiện có.
  - **Migration:** `ops/migrations/20260825_05_remove_rented_warehouse_status.sql` chuyển RENTED → AVAILABLE trước khi thu hẹp DB constraint.
  - **Search gate:** `rg "RENTED|markAsRented" src/main/java src/test` chỉ còn migration/comment lịch sử được chấp nhận.
  - **Test trong commit:** Warehouse lifecycle, inspection, search, layout owner update, stats/chatbot.
  - **Hoàn thành khi:** nhiều Contract ACTIVE không làm bài đăng biến mất hoặc khóa Warehouse.
  - **Commit hoàn thành:** `3ff8dd2`

## B9. Finalize database constraints và loại cột chuyển tiếp

- [x] Chốt migration sau khi application không còn phụ thuộc legacy relations
  - **Commit đề xuất:** `chore(db): finalize direct rental contract schema`
  - **Preflight:** `ops/maintenance/rental_contract_refactor_preflight.sql` phải kiểm tra:
    - direct owner/tenant/warehouse null hoặc FK invalid.
    - pricing/dimensions/final rent invalid.
    - active/pending overlap cùng Tenant–Warehouse.
    - legacy statuses theo số lượng.
    - Warehouse RENTED còn sót.
    - active Contract không có tenant layout.
  - **Final migration:** `ops/migrations/20260825_06_finalize_rental_contract_refactor.sql`.
  - **Legacy status mapping đề xuất:**
    - `ACTIVE` giữ ACTIVE.
    - `COMPLETED`, `PENDING_HANDOVER` → EXPIRED.
    - `UNDER_NEGOTIATION`, `PENDING_TERMINATION`, `PENDING_CANCEL`, `CANCELLED`, `DISPUTED` → REJECTED và ghi migration reason nếu field trống.
    - `PENDING_TENANT_CONFIRM` cũ chỉ giữ nếu mọi field/pricing/layout mới hợp lệ; nếu không → REJECTED để không gửi Contract thiếu dữ liệu cho Tenant.
  - **Constraints/cleanup:**
    - Set direct FKs, status, dates, dimensions, pricing fields NOT NULL theo từng pricing mode.
    - Thu hẹp status check còn sáu giá trị mới.
    - Drop `booking_id` khỏi `rental_contracts` sau verification; giữ bảng Booking lịch sử độc lập.
    - Drop cột contract cũ không còn dùng: `tenant_confirmed`, `owner_confirmed`, cancel/handover/deposit termination fields sau khi preflight xác nhận.
    - Drop `price_per_month` sau khi `rental_price` đã backfill và toàn bộ code dùng tên mới.
    - Không drop transaction legacy fields/types cần cho lịch sử.
    - Thêm index/query indexes cuối và constraint dimensions/positive money.
  - **Post-check:** file SQL riêng hoặc cuối migration đếm zero invalid rows; application startup không để Hibernate tự tạo lại cột cũ.
  - **Test trong commit:** schema/migration smoke test nếu hạ tầng cho phép; tối thiểu repository/entity mapping + verify trên PostgreSQL local.
  - **Hoàn thành khi:** schema production-target không còn cột chuyển tiếp trong Contract/Warehouse và app startup sạch.
  - **Commit hoàn thành:** `019b662`

## B10. Final regression, OpenAPI và FE handoff

- [x] Chốt backend để FE có thể triển khai không phải suy đoán API
  - **Commit đề xuất:** `test(api): finalize rental refactor contract`
  - **OpenAPI:** tất cả endpoint mới có summary, request/response schema, status/action flags và error examples; endpoint cũ không còn xuất hiện.
  - **Error contract tối thiểu:** `WAREHOUSE_NOT_OWNED`, `TENANT_NOT_FOUND/INVALID_ROLE`, `CONTRACT_NOT_FOUND`, `INVALID_CONTRACT_STATUS`, `CONTRACT_DATE_OVERLAP`, `INVALID_LEASE_DIMENSIONS`, `SUBSCRIPTION_REQUIRED`, `LISTING_PACKAGE_INACTIVE`, `INSUFFICIENT_BALANCE`.
  - **Regression E2E:**
    - Warehouse create → Admin approve → Owner mua listing package → public search thấy bài.
    - Tenant login → owner contact.
    - Owner create Draft → setup layout → submit.
    - Tenant request changes → Owner resubmit → Tenant confirm.
    - No Subscription: layout read-only, WMS mutations denied.
    - Buy Subscription: layout/WMS unlocked.
    - Tenant B cũng ACTIVE trên cùng Warehouse.
    - Same Tenant overlapping Contract denied.
    - Contract expiry cleanup đúng và listing vẫn tồn tại nếu publication còn hạn.
    - Subscription expiry khóa mutation nhưng không xóa Contract/layout observation.
  - **Static gates:**
    - Không active reference `contract.booking`, BookingService, rental deposit, Dispute, Warehouse.RENTED.
    - Không public phone leak.
    - Không WMS mutation thiếu Subscription gate.
  - **Command:** `.\mvnw.cmd verify` và CI xanh.
  - **Handoff FE:** xuất Swagger/OpenAPI JSON hoặc link environment; dùng phụ lục API cuối trong tài liệu này làm checklist tích hợp.
  - **Hoàn thành khi:** tất cả checkbox B1–B9 có hash, full verify xanh, migration preflight/post-check xanh trên bản sao database.
  - **Kết quả verify:** `PASS — 349 tests, 0 failures, 0 errors, 2 skipped`.
  - **Kết quả static gate:** sạch; chỉ còn `DEPOSIT_PAYMENT`, `DEPOSIT_RECEIVED`, `DEPOSIT_REFUND` trong enum transaction lịch sử được cho phép.
  - **Kết quả migration:** preflight/post-check đều zero invalid rows trên PostgreSQL 17 representative legacy fixture; migration chạy lại lần hai thành công.
  - **Swagger/OpenAPI runtime:** `/v3/api-docs`; Swagger UI: `/swagger-ui/index.html`.
  - **Commit hoàn thành / Final backend HEAD:** `59c201c`

---

# PHỤ LỤC 1 — API backend cuối cùng FE được phép dựa vào

## Warehouse listing và contact

| Method | Endpoint | Auth | Mục đích |
|---|---|---|---|
| GET | `/api/listing-packages` | Public | Danh sách combo đăng bài active |
| POST | `/api/owner/warehouses/{id}/publications` | Owner | Mua/gia hạn thời gian hiển thị |
| GET | `/api/owner/warehouses/{id}/publications` | Owner | Lịch sử mua gói của kho |
| GET | `/api/warehouses` | Public | Chỉ bài approved và còn hạn |
| GET | `/api/warehouses/{id}` | Public | Chi tiết không lộ owner phone |
| GET | `/api/warehouses/{id}/owner-contact` | Registered user | Lấy phone Owner |

Warehouse create/update fields mới:

```json
{
  "rentalPricingType": "FIXED_MONTHLY | PER_SQUARE_METER_MONTHLY | NEGOTIATED",
  "rentalPrice": 100000000
}
```

`rentalPrice` bắt buộc cho FIXED/PER_M2, phải null cho NEGOTIATED.

## Contract Owner API

| Method | Endpoint | Status cho phép |
|---|---|---|
| POST | `/api/owner/contracts/preview` | Không ghi dữ liệu |
| POST | `/api/owner/contracts` | Tạo DRAFT |
| PUT | `/api/owner/contracts/{id}` | DRAFT, CHANGES_REQUESTED |
| DELETE | `/api/owner/contracts/{id}` | DRAFT |
| GET | `/api/owner/contracts/{id}/layout` | Contract của Owner |
| PUT | `/api/owner/contracts/{id}/layout` | DRAFT, CHANGES_REQUESTED |
| POST | `/api/owner/contracts/{id}/submit` | DRAFT, CHANGES_REQUESTED |

## Contract Tenant API

| Method | Endpoint | Status cho phép |
|---|---|---|
| GET | `/api/tenant/contracts/{id}/layout` | Read-only theo policy |
| POST | `/api/tenant/contracts/{id}/confirm` | PENDING_TENANT_CONFIRM |
| POST | `/api/tenant/contracts/{id}/request-changes` | PENDING_TENANT_CONFIRM |
| POST | `/api/tenant/contracts/{id}/reject` | PENDING_TENANT_CONFIRM |

## Contract common API

| Method | Endpoint | Auth |
|---|---|---|
| GET | `/api/contracts` | Owner/Tenant của Contract |
| GET | `/api/contracts/{id}` | Owner/Tenant của Contract |

Contract create request cuối:

```json
{
  "warehouseId": "uuid",
  "tenantEmail": "tenant@example.com",
  "startDate": "2026-09-01",
  "endDate": "2027-08-31",
  "leasedWidth": 10,
  "leasedLength": 8,
  "leasedHeight": 4,
  "negotiatedMonthlyRent": 12000000,
  "paperContractFiles": ["https://..."],
  "ownerNote": "Optional note"
}
```

`negotiatedMonthlyRent` chỉ gửi khi Warehouse pricing type là NEGOTIATED.

Contract response tối thiểu:

```json
{
  "id": "uuid",
  "status": "PENDING_TENANT_CONFIRM",
  "ownerId": "uuid",
  "ownerName": "Owner",
  "tenantId": "uuid",
  "tenantName": "Tenant",
  "tenantEmail": "tenant@example.com",
  "warehouseId": "uuid",
  "warehouseName": "Warehouse A",
  "pricingType": "PER_SQUARE_METER_MONTHLY",
  "rentalPriceSnapshot": 200000,
  "leasedWidth": 10,
  "leasedLength": 8,
  "leasedHeight": 4,
  "leasedAreaM2": 80,
  "finalMonthlyRent": 16000000,
  "paperContractFiles": ["https://..."],
  "changeRequestReason": null,
  "rejectionReason": null,
  "submittedAt": "2026-08-25T10:00:00",
  "confirmedAt": null,
  "canEdit": false,
  "canDelete": false,
  "canSubmit": false,
  "canConfirm": true,
  "canRequestChanges": true,
  "canReject": true,
  "canViewLayout": true,
  "canManageWms": false
}
```

## State/action matrix FE

| Status | Owner | Tenant |
|---|---|---|
| DRAFT | Edit layout/terms, submit, soft-delete | Không thấy hoặc chỉ thấy nếu policy cho phép |
| PENDING_TENANT_CONFIRM | View | Confirm, request changes, reject, view layout |
| CHANGES_REQUESTED | Edit layout/terms, resubmit | View reason/layout |
| ACTIVE | View | View layout; manage WMS chỉ khi Subscription ACTIVE |
| REJECTED | View history | View history/reason |
| EXPIRED | View history | View history/read-only theo policy |

---

# PHỤ LỤC 2 — File/module search gate trước khi đánh dấu Done

VINH phải chạy và xử lý từng kết quả thực tế, không chỉ dựa vào danh sách này:

```powershell
rg -n "contract\.booking|getBooking\(\)" src/main/java src/test
rg -n "BookingService|createContractFromBooking" src/main/java src/test
rg -n "deposit_percentage|DEPOSIT_PAYMENT|DEPOSIT_RECEIVED|DEPOSIT_REFUND" src/main/java src/test
rg -n "WarehouseStatus\.RENTED|markAsRented|RENTED" src/main/java src/test
rg -n "DisputeService|DisputeController|PENDING_CANCEL|PENDING_HANDOVER|DISPUTED" src/main/java src/test
rg -n "pricePerMonth|price_per_month" src/main/java src/test
rg -n "ownerPhone" src/main/java/fu/stockspace/stockspace_be/warehouse
```

Kết quả được phép còn lại chỉ là:

- Migration/preflight mô tả dữ liệu legacy.
- Transaction enum/DTO cần đọc lịch sử deposit cũ.
- Comment tài liệu migration có ghi rõ legacy.

---

# PHỤ LỤC 3 — Handoff record

## VIỆT ANH → VINH

- Baseline dev commit: `1bab796`
- Branch: `refactor/rental-contract-dev-b`
- A12 HEAD: `f055a0e`
- Full verify: `PASS (340 tests passed, 2 skipped)`
- Migration preflight file/result: `ops/maintenance/rental_contract_refactor_preflight.sql` — `PASS` trên PostgreSQL 17 representative legacy fixture.
- Known issue được chấp nhận trước Phần B: legacy Booking/deposit/Dispute/RENTED và các consumer phụ thuộc được giao cho B1–B9 cleanup.

## VINH → Team/FE

- Branch: `refactor/rental-contract-dev-b`
- B10 final HEAD: `59c201c`
- Full verify: `PASS — 349 tests, 0 failures, 0 errors, 2 skipped`
- Production-copy migration preflight: `PENDING external database access`; PostgreSQL 17 representative legacy fixture: `PASS`
- Post-migration verification: representative fixture `PASS`; idempotent second run `PASS`
- Swagger/OpenAPI location: `/v3/api-docs`; `/swagger-ui/index.html`
- Known limitation còn lại: phải chạy lại preflight/migration/post-check trên controlled production copy trước deploy; legacy deposit transaction enum/fields được giữ read-only để đọc lịch sử.

## Definition of Done toàn bộ Backend (Gate, không phải nhiệm vụ commit)

- Tất cả checkbox A1–A12 và B1–B10 có commit hash.
- Full Maven verify xanh.
- Migration chạy thành công trên bản sao database và post-check không có invalid row.
- FE có endpoint/schema/action matrix cuối, không cần gọi Booking/Deposit/Dispute API.
- Contract ACTIVE chỉ cấp read-only; Subscription ACTIVE mới mở mutation hậu thuê.
- Nhiều Tenant thuê cùng Warehouse được; cùng Tenant–Warehouse không có Contract chồng ngày.
- Public phone không bị lộ cho Guest.
- Listing fee, rental price và final contract rent là ba khái niệm/API field riêng.
- Không còn active code phụ thuộc Booking, RENTED, rental deposit hoặc Dispute.
- Chưa deploy production cho đến khi toàn bộ Gate cuối hoàn tất.
