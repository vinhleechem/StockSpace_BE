# Kế hoạch xử lý hết hạn, gia hạn hợp đồng và quyền WMS

## 1. Kết luận thiết kế

StockSpace cần tách ba vòng đời độc lập:

1. **Hợp đồng thuê kho**: xác định Tenant có quyền sử dụng Warehouse nào và trong khoảng ngày nào.
2. **Subscription WMS của Tenant**: xác định Tenant có được dùng các chức năng vận hành trả phí hay không.
3. **Publication package của Owner**: chỉ xác định bài đăng Warehouse có hiển thị công khai hay không.

Ba vòng đời này không được cập nhật trạng thái thay cho nhau:

- Hợp đồng hết hạn không làm Subscription hết hạn.
- Subscription hết hạn không làm hợp đồng hoặc dữ liệu kho bị xóa.
- Bài đăng hết hạn không làm hợp đồng hiện hữu hoặc WMS của Tenant bị khóa.
- Owner không cần mua Subscription WMS để quản lý Warehouse mặc định, bài đăng và hợp đồng của mình.

## 2. Vấn đề của code hiện tại

### 2.1 Expiry đang cleanup quá sớm

`ContractExpiryScheduler` hiện thực hiện đồng thời:

- `ACTIVE -> EXPIRED`;
- soft-delete toàn bộ `StockBatch` của Tenant tại Warehouse;
- archive tenant layout;
- revoke toàn bộ staff assignment.

Đây là thao tác khó phục hồi và gây lỗi khi Tenant đang chờ gia hạn, gia hạn muộn hoặc còn hàng chưa xuất hết.

### 2.2 Chưa có nghiệp vụ renewal

`RentalContract` chưa có liên kết hợp đồng gốc/hợp đồng kế tiếp và API chưa có action `renew`. Plan cũ cũng chủ động để renewal ngoài scope.

### 2.3 `ACTIVE` đang mang hai nghĩa

Tenant có thể confirm một hợp đồng bắt đầu trong tương lai và record được chuyển ngay sang `ACTIVE`. Repository kiểm tra ngày khi cấp quyền, nhưng `canManageWms` trong response chỉ kiểm tra status và Subscription nên có thể trả sai quyền trước `startDate` hoặc sau `endDate` nếu scheduler chưa chạy.

### 2.4 Contract layout và operational layout chưa tách hẳn

Mỗi cặp Tenant–Warehouse hiện chỉ có một tenant layout vận hành. Một renewal draft có thể cần snapshot layout đang chạy, nhưng không được phép để Owner chỉnh renewal draft rồi làm thay đổi layout live của hợp đồng hiện tại.

### 2.5 Quyền WMS đang được kiểm tra phân tán

Các service tự ghép `active contract`, `active subscription` và `staff assignment` theo nhiều cách khác nhau. Điều này làm read/write, Tenant/Staff và response action flags dễ lệch nhau.

## 3. Mô hình nghiệp vụ đích

### 3.1 Hợp đồng thuê

Không sửa trực tiếp `endDate` của hợp đồng đã confirm. Gia hạn tạo **một RentalContract mới** để giữ lịch sử và file hợp đồng/phụ lục của từng kỳ.

Thêm tối thiểu:

- `renewed_from_contract_id`: nullable FK tới `rental_contracts.id`;
- `renewal_sequence`: `0` với hợp đồng đầu, tăng dần cho mỗi lần gia hạn;
- `contract_kind`: `INITIAL` hoặc `RENEWAL` (có thể tính từ `renewedFrom`, nhưng field giúp query/report rõ hơn);
- `offboarding_started_at`, `offboarding_deadline`: nullable, chỉ dùng khi kỳ thuê kết thúc mà chưa có successor có hiệu lực.

Không cần thêm `PENDING_RENEWAL` vào `ContractStatus`. Renewal vẫn đi qua workflow có sẵn:

```text
DRAFT
  -> PENDING_TENANT_CONFIRM
  -> CHANGES_REQUESTED -> PENDING_TENANT_CONFIRM
  -> CONFIRMED
  -> REJECTED
```

Để tránh `ACTIVE` bị hiểu sai, API trả thêm `termStatus` được tính từ ngày:

```text
UPCOMING  : đã confirm, today < startDate
ACTIVE    : startDate <= today <= endDate
EXPIRING  : ACTIVE và còn <= reminderDays
OFFBOARDING: đã quá endDate, chưa đóng workspace
ENDED     : đã đóng workspace hoặc chỉ còn lịch sử
```

MVP có thể giữ enum DB `ACTIVE/EXPIRED` để migration nhẹ, nhưng mọi kiểm tra quyền và action flag phải dùng `termStatus`/date range, không chỉ dùng enum.

### 3.2 Tenant–Warehouse workspace

Layout, stock và staff assignment là dữ liệu vận hành của **mối quan hệ Tenant–Warehouse**, không phải dữ liệu riêng của một kỳ hợp đồng.

Nên thêm bảng điều phối `tenant_warehouse_workspaces`:

- `id`;
- `tenant_id`, `warehouse_id`;
- `operational_layout_id`;
- `status`: `PREPARING`, `ACTIVE`, `READ_ONLY`, `OFFBOARDING`, `ARCHIVED`;
- `current_contract_id`;
- `offboarding_deadline`;
- `version` để optimistic locking;
- unique `(tenant_id, warehouse_id)` cho workspace chưa archive.

Ở phase đầu, các bảng WMS chưa bắt buộc phải thêm `workspace_id`; vẫn có thể query theo Tenant–Warehouse. Workspace chỉ là aggregate điều phối quyền và lifecycle.

### 3.3 Hai loại layout

- **Contract layout snapshot**: JSON bất biến nằm trong từng hợp đồng, dùng để hai bên review/audit.
- **Operational tenant layout**: `WarehouseLayout` live duy nhất của Tenant–Warehouse, được WMS sử dụng.

Rule renewal MVP:

- Renewal mặc định copy snapshot từ operational layout hiện tại.
- Cho đổi ngày thuê, giá thuê, note và file giấy/phụ lục.
- Chưa cho đổi kích thước leased layout trong renewal MVP.
- Nếu cần đổi kích thước, xử lý ở phase sau bằng `layout revision` có `effectiveFrom`; tuyệt đối không sửa layout live trước ngày renewal bắt đầu.

## 4. Ma trận quyền duy nhất

| Điều kiện | Xem hợp đồng/layout | Xem tồn kho/lịch sử | Mutation WMS | Owner sửa default layout |
|---|---:|---:|---:|---:|
| Contract hiện hành + Subscription hiện hành | Có | Có | Có | Không liên quan |
| Contract hiện hành + Subscription hết hạn | Có | Có | Không | Không liên quan |
| Subscription hiện hành + không có Contract trên Warehouse | Không | Không | Không | Không liên quan |
| Contract UPCOMING | Có snapshot Contract | Không có quyền live trước ngày bắt đầu | Không | Không liên quan |
| OFFBOARDING | Có | Có | Chỉ action offboarding được phép | Không liên quan |
| Contract ENDED | Có lịch sử/snapshot | Chỉ lịch sử/export theo policy | Không | Không liên quan |
| Owner sở hữu Warehouse | Quản lý Contract theo workflow | Không mặc định xem dữ liệu WMS của Tenant | Không | Có, không cần WMS Subscription |
| Publication hết hạn | Không ảnh hưởng các quyền trên | Không ảnh hưởng | Không ảnh hưởng | Có |

Staff chỉ được hưởng quyền của Tenant khi đồng thời có assignment hợp lệ. Staff không tự có quyền chỉ vì giữ RBAC permission.

## 5. Flow gia hạn đề xuất

### 5.1 Tạo renewal

```text
Owner mở Contract đang ACTIVE/EXPIRING
-> chọn Renew
-> BE tạo renewal DRAFT từ Contract cũ
-> startDate bắt buộc = old.endDate + 1 ngày
-> Owner cập nhật endDate, giá, note, file phụ lục
-> Owner submit
-> Tenant confirm/request changes/reject như flow hiện tại
```

Validation:

- Chỉ Owner của hợp đồng được tạo renewal.
- Chỉ cho renewal từ hợp đồng đã confirm và không bị reject/delete.
- Mỗi hợp đồng chỉ có tối đa một successor đang actionable (`DRAFT`, `CHANGES_REQUESTED`, `PENDING_TENANT_CONFIRM`, confirmed/upcoming).
- Tenant và Warehouse không được thay đổi.
- Ngày nối tiếp không có gap trong flow renewal chuẩn.
- Date overlap vẫn bị cấm theo cặp Tenant–Warehouse.
- Renewal MVP giữ nguyên leased dimensions.

### 5.2 Khi Tenant confirm sớm

- Renewal trở thành confirmed/upcoming.
- Không cấp quyền live trước `startDate`.
- Không thay `currentContractId` của workspace trước ngày bắt đầu.
- Đến ngày bắt đầu, resolver chọn renewal là current contract.
- Hợp đồng cũ được đánh dấu hết kỳ nhưng workspace, layout, stock và assignment được giữ nguyên.

### 5.3 Khi renewal bị reject hoặc chưa confirm

- Hợp đồng cũ tiếp tục chạy tới `endDate`.
- Renewal không ảnh hưởng quyền hiện tại.
- Sau khi hợp đồng cũ hết hạn, nếu không có successor confirmed/current thì workspace chuyển `OFFBOARDING`.

## 6. Flow hết hạn và offboarding

Không được tự động soft-delete stock/layout ngay sau nửa đêm.

### 6.1 Trước ngày hết hạn

- Reminder lần 1: trước 30 ngày.
- Reminder lần 2: trước 7 ngày nếu chưa có renewal confirmed.
- Reminder lần 3: trước 1 ngày.
- Response trả `canRenew`, `daysUntilExpiry`, `hasPendingRenewal` để FE không tự suy đoán.

### 6.2 Sau `endDate`

Scheduler chạy idempotent theo thứ tự:

1. Lock Tenant–Warehouse workspace.
2. Đánh dấu kỳ hợp đồng cũ hết hạn.
3. Tìm successor đã confirm và có `startDate <= today`.
4. Nếu có successor liên tục: đổi `currentContractId`, giữ workspace `ACTIVE`, không cleanup.
5. Nếu không có successor: chuyển workspace `OFFBOARDING`, đặt deadline theo config.
6. Gửi notification theo event/outbox; không để lỗi email rollback lifecycle.

### 6.3 Quyền trong OFFBOARDING

Mặc định đề xuất grace period 7 ngày:

- cho đọc layout, tồn kho và lịch sử;
- cho export dữ liệu;
- cho hoàn tất outbound đang mở và tạo outbound phục vụ dọn kho;
- chặn inbound, putaway mới, transfer vào kho, audit mới, sửa layout và thêm staff assignment;
- không yêu cầu Subscription WMS cho các action dọn kho tối thiểu, tránh khóa hàng của Tenant chỉ vì gói dịch vụ cũng hết hạn.

Trước khi archive phải kiểm tra:

- tổng tồn kho bằng 0;
- không còn receipt/transfer/audit ở trạng thái mở;
- không có renewal confirmed/current.

Nếu chưa đạt điều kiện, giữ `OFFBOARDING` và cảnh báo; không xóa dữ liệu tự động.

Khi đủ điều kiện, action `complete-offboarding` sẽ:

- chuyển workspace `ARCHIVED`;
- archive operational layout;
- kết thúc/revoke staff assignment của Warehouse;
- giữ toàn bộ stock transaction, receipt, audit và contract snapshot làm lịch sử.

## 7. API thay đổi

### 7.1 Renewal

```http
POST /api/owner/contracts/{contractId}/renewals/preview
POST /api/owner/contracts/{contractId}/renewals
GET  /api/contracts/{contractId}/renewal
```

Sau khi tạo draft, tái sử dụng API update/submit/review hiện có.

Response Contract bổ sung:

- `contractKind`;
- `renewedFromContractId`;
- `renewalContractId`;
- `termStatus`;
- `daysUntilExpiry`;
- `canRenew`;
- `hasPendingRenewal`;
- `workspaceStatus`;
- `allowedCapabilities` thay cho việc FE tự ghép nhiều boolean rời rạc.

### 7.2 Offboarding

```http
GET  /api/tenant/warehouses/{warehouseId}/offboarding
POST /api/tenant/warehouses/{warehouseId}/offboarding/complete
POST /api/owner/warehouses/{warehouseId}/tenants/{tenantId}/offboarding/complete
```

Owner complete chỉ nên dùng khi các precondition hệ thống đều đạt; không cho Owner xóa cưỡng bức stock history.

## 8. Refactor access policy

Đổi `TenantWarehouseAccessService` thành nguồn quyết định duy nhất:

```text
OBSERVE_CONTRACT
OBSERVE_OPERATIONAL_DATA
OPERATE_WMS
OFFBOARD_WMS
MANAGE_STAFF
EDIT_TENANT_LAYOUT
```

Service trả `WarehouseAccessDecision`, chứa:

- contract và `termStatus` hiện hành;
- subscription hiện hành;
- workspace status;
- staff assignment nếu actor là Staff;
- tập capability cuối cùng.

Controller/service chỉ gọi `requireCapability(...)`. `RentalContractResponse.canManageWms` và các action flags cũng phải lấy từ cùng resolver này.

## 9. Thứ tự triển khai

### Phase 0 — Chốt rule và thêm regression guard

1. Chốt `endDate` là ngày sử dụng cuối cùng; renewal bắt đầu `endDate + 1`.
2. Chốt renewal MVP không đổi leased dimensions.
3. Viết test tái hiện các lỗi hiện tại: future contract trả `canManageWms`, expiry cleanup khi có renewal, renewal draft sửa layout live.

### Phase 1 — Safety patch

1. Scheduler hết hạn ngừng soft-delete stock/layout/staff.
2. Access flags kiểm tra đầy đủ date range và `isActive/isDeleted` của Subscription.
3. Tách tên config:
   - `contract_review_expiry_days` cho thời hạn Tenant phản hồi;
   - `contract_renewal_reminder_days`;
   - `contract_offboarding_grace_days`.
4. Chuẩn hóa read-only và mutation checks trong `TenantWarehouseAccessService`.

### Phase 2 — Renewal MVP

1. Migration thêm renewal link/sequence/kind.
2. Thêm create/preview renewal API.
3. Tái sử dụng submit và tenant review workflow.
4. Giữ layout/stock/staff khi successor bắt đầu liên tục.
5. FE thêm CTA Renew, renewal badge và timeline hợp đồng.

### Phase 3 — Workspace và offboarding

1. Thêm `tenant_warehouse_workspaces`, backfill theo các cặp Tenant–Warehouse hiện có.
2. Scheduler chỉ chuyển workspace state.
3. Thêm capability `OFFBOARD_WMS` và endpoint complete.
4. Chỉ archive sau khi precondition đạt.

### Phase 4 — Hardening

1. Outbox cho notification và audit event.
2. Lock/optimistic version chống scheduler, confirm renewal và offboarding chạy đồng thời.
3. Partial unique index ngăn nhiều successor actionable.
4. Dashboard, chatbot và FE chỉ đọc `termStatus/workspaceStatus/capabilities` từ BE.

## 10. Commit sequence đề xuất

1. `test(contract): cover renewal and expiry boundary cases`
2. `fix(contract): stop destructive cleanup on expiry`
3. `refactor(access): centralize tenant warehouse capabilities`
4. `fix(contract): derive WMS flags from current term and subscription`
5. `feat(contract): add renewal chain metadata`
6. `feat(contract): support owner-created renewal drafts`
7. `feat(contract): activate contiguous renewal without workspace cleanup`
8. `feat(workspace): add tenant warehouse lifecycle state`
9. `feat(contract): support controlled offboarding`
10. `docs(api): document renewal and WMS lifecycle matrix`

## 11. Test matrix bắt buộc

- Hợp đồng cũ kết thúc hôm nay vẫn dùng được hết hôm nay.
- Renewal bắt đầu ngày mai không có downtime tại boundary.
- Renewal confirmed nhưng chưa tới `startDate` không cấp quyền sớm.
- Renewal pending/rejected không làm thay đổi hợp đồng hiện tại.
- Scheduler chạy lặp không cleanup hoặc notify trùng.
- Subscription hết hạn chỉ khóa mutation trả phí; không xóa Contract/layout/stock/staff.
- Publication hết hạn chỉ ẩn bài public.
- Staff cần đủ current contract, Tenant capability và active assignment.
- Expiry có stock/open receipt/open transfer không được archive.
- Late renewal trong grace period có thể khôi phục workspace mà không mất dữ liệu.
- Hai request renewal đồng thời chỉ tạo được một successor actionable.
- Owner không thể sửa operational tenant layout thông qua renewal draft.

## 12. Phạm vi MVP nên giữ

Để kịp và ít rủi ro, MVP chỉ làm:

- renewal bằng hợp đồng mới có link;
- cùng Tenant, Warehouse và leased dimensions;
- giá/ngày/file hợp đồng được thay đổi;
- giữ nguyên operational layout, stock và staff nếu renewal liên tục;
- nếu không renewal thì chuyển read-only/offboarding, không xóa tự động.

Các phần đổi diện tích giữa hai kỳ, tự động tính tiền thuê, thu tiền cọc, dispute và e-sign nên để ngoài scope hiện tại.
