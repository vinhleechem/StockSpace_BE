# Hướng Dẫn & Danh Sách Công Việc Hệ Thống Authentication & Phân Quyền (Full RBAC)

Tài liệu này lưu trữ lại toàn bộ các hạng mục công việc đã thực hiện (Dev 1) và các hạng mục cần tiếp tục triển khai (Dev 2) cho module Authentication, Refresh Token và phân quyền Full RBAC của dự án **StockSpace_BE**.

---

## 🗺️ Mô Hình Phân Quyền (Full RBAC Schema)
Hệ thống chuyển đổi từ phân quyền tĩnh (role enum) sang phân quyền động (lưu bảng trong cơ sở dữ liệu), cho phép quản lý chi tiết các quyền hạn cụ thể (Permissions) và gán cho các vai trò (Roles) tương ứng.

```
users ──────────── user_roles ─────────── roles ──── role_permissions ──── permissions
├── id (UUID)      ├── user_id            ├── id (UUID)├── role_id           ├── id (UUID)
├── email          └── role_id            ├── name     └── permission_id     ├── name
├── password                              └── desc                           └── desc
└── ...
```

---

## 🧑‍💻 Hạng Mục 1: Nền Tảng Auth & DB (Dev 1) — [ĐÃ HOÀN THÀNH ✅]

### 1. Database & Entities
- [x] **`RoleType.java` (Enum)**: Định nghĩa các role mặc định trong hệ thống để phục vụ validation và seed dữ liệu.
- [x] **`Role.java` (Entity)**: Chuyển đổi từ Enum thành Entity JPA, map với bảng `roles`, có mối quan hệ `@ManyToMany` với `Permission`.
- [x] **`Permission.java` (Entity)**: Tạo mới đại diện cho các quyền cụ thể (ví dụ: `WAREHOUSE_CREATE`, `INVENTORY_READ`), map với bảng `permissions`.
- [x] **`User.java` (Entity)**: Thay thế trường `role` enum đơn lẻ thành `Set<Role> roles` (nhiều vai trò). Cập nhật `getAuthorities()` để nạp đồng thời cả Roles (dạng `ROLE_ADMIN`) và Permissions (dạng `WAREHOUSE_CREATE`) vào Spring Security Context.
- [x] **Repository**:
  - `RoleRepository.java` (JPA): Tìm kiếm role theo tên (`findByName`).
  - `PermissionRepository.java` (JPA): Tìm kiếm permission theo tên (`findByName`).
- [x] **Exception Handling**: Chuẩn hóa toàn bộ lỗi nghiệp vụ bằng cách kết hợp giữa enum `ErrorCode` và các class Custom Exception được chia nhỏ đại diện cho từng mã trạng thái HTTP:
  - `BadRequestException` (HTTP 400)
  - `UnauthorizedException` (HTTP 401)
  - `ForbiddenException` (HTTP 403)
  - `ResourceNotFoundException` (HTTP 404)
  - `ResourceConflictException` (HTTP 409)
  - `InternalServerException` (HTTP 500)
  Tránh hoàn toàn việc lạm dụng `IllegalArgumentException` chung chung.

### 2. JWT & Security Utilities
- [x] **`JwtUtil.java`**:
  - Đưa toàn bộ danh sách vai trò vào claim `roles` (dạng chuỗi phân tách bởi dấu phẩy).
  - Vẫn giữ claim `role` chứa vai trò chính (primary role) để đảm bảo tương thích ngược với Frontend.
  - Hỗ trợ phương thức `extractRoles(String token)`.
- [x] **`SecurityUtil.java`**:
  - `getCurrentRole()` lấy vai trò chính của user từ Security Context.
  - `hasRole(Role role)` kiểm tra xem user có chứa role cụ thể hay không qua Collection.

### 3. DTOs & Services
- [x] **`RegisterRequest.java`**: Đổi kiểu dữ liệu của trường `role` từ entity `Role` sang enum `RoleType`.
- [x] **`LoginResponse.java`**: Trả về trường `role` dưới dạng `String` (tên của vai trò chính).
- [x] **`AuthService.java`**:
  - Logic đăng ký (`register`) tự động tìm kiếm Role tương ứng trong DB và gán cho User.
  - Logic đăng nhập (`login`) ghi nhận thông tin đăng nhập kèm tất cả vai trò hiện có.
- [x] **`DataInitializer.java`**:
  - Khởi tạo tự động **18 Permissions mặc định** và **5 Roles mặc định** khi ứng dụng chạy lần đầu.
  - Thiết lập gán quyền mặc định cho từng vai trò.
  - Tạo sẵn **5 tài khoản kiểm thử mặc định** tương ứng với 5 vai trò (mật khẩu mặc định: `Password123`).

---

## 🧑‍💻 Hạng Mục 2: API Quản Lý Của Admin (Dev 2) — [CẦN LÀM ⏳]
Hạng mục này phục vụ việc cấu hình, tạo mới, chỉnh sửa vai trò, quyền hạn trực tiếp từ giao diện Admin của hệ thống.

### 1. DTOs Cần Thiết (Được đặt trong package `fu.stockspace.stockspace_be.admin.dto`)
- [ ] **`CreateRoleRequest.java`**: Dùng để tạo/cập nhật Role (gồm `name`, `description`).
- [ ] **`AssignPermissionRequest.java`**: Dùng để gán quyền cho vai trò (chứa `permissionId`).
- [ ] **`AssignRoleRequest.java`**: Dùng để gán vai trò cho người dùng (chứa `roleId`).
- [ ] **`RoleResponse.java`**: DTO trả về thông tin chi tiết của vai trò (gồm `id`, `name`, `description`, danh sách `permissions` đi kèm).

### 2. Services Xử Lý Logic (Trong package `fu.stockspace.stockspace_be.admin.service`)
- [ ] **`RoleManagementService.java`**:
  - Lấy danh sách tất cả các vai trò trong hệ thống.
  - Tạo mới, cập nhật, xóa vai trò.
  - Gán quyền (Permission) vào vai trò (Role).
  - Gỡ bỏ quyền khỏi vai trò.
  - Gán vai trò cho User (thêm vào bảng `user_roles`).
  - Gỡ vai trò khỏi User.
- [ ] **`PermissionManagementService.java`**:
  - Xem danh sách quyền.
  - Tạo mới quyền (chỉ dành cho các tính năng đặc thù phát sinh sau này).

### 3. Controller Endpoints (Trong package `fu.stockspace.stockspace_be.admin.controller`)

| HTTP Method | Endpoint Path | Yêu Cầu Auth | Mô Tả Tính Năng |
|-------------|---------------|--------------|-----------------|
| **GET** | `/api/admin/roles` | `ROLE_ADMIN` | Xem danh sách tất cả các vai trò |
| **POST** | `/api/admin/roles` | `ROLE_ADMIN` | Tạo vai trò mới (Ví dụ: `ROLE_SUPERVISOR`) |
| **PUT** | `/api/admin/roles/{id}` | `ROLE_ADMIN` | Sửa đổi thông tin vai trò |
| **DELETE** | `/api/admin/roles/{id}` | `ROLE_ADMIN` | Xóa vai trò |
| **POST** | `/api/admin/roles/{id}/permissions` | `ROLE_ADMIN` | Gán thêm một Permission vào Role |
| **DELETE** | `/api/admin/roles/{id}/permissions/{permId}` | `ROLE_ADMIN` | Gỡ bỏ Permission khỏi Role |
| **GET** | `/api/admin/permissions` | `ROLE_ADMIN` | Xem tất cả Permissions hiện có |
| **POST** | `/api/admin/permissions` | `ROLE_ADMIN` | Tạo mới một Permission |
| **POST** | `/api/admin/users/{userId}/roles` | `ROLE_ADMIN` | Gán vai trò (Role) cho User |
| **DELETE** | `/api/admin/users/{userId}/roles/{roleId}` | `ROLE_ADMIN` | Xóa vai trò (Role) khỏi User |

---

## 💡 Hướng Dẫn Sử Dụng Phân Quyền Trong Code (Cho Feature Developers)

Kể từ phiên bản Full RBAC này, lập trình viên có thể bảo vệ API bằng 2 cách thông qua annotation `@PreAuthorize`:

### 1. Phân quyền theo Nhóm Vai Trò (Role-based) - Phù hợp với API chung
```java
// Chỉ Admin được truy cập
@PreAuthorize("hasRole('ADMIN')")

// Chỉ Warehouse Owner hoặc Tenant được truy cập
@PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
```

### 2. Phân quyền theo Quyền Hạn Chi Tiết (Permission-based) - Khuyên dùng cho các chức năng nghiệp vụ
```java
// Tạo kho hàng yêu cầu quyền WAREHOUSE_CREATE
@PreAuthorize("hasAuthority('WAREHOUSE_CREATE')")

// Xem danh sách tồn kho yêu cầu quyền INVENTORY_READ
@PreAuthorize("hasAuthority('INVENTORY_READ')")

// Tạo phiếu nhập hoặc xuất yêu cầu một trong hai quyền tương ứng
@PreAuthorize("hasAnyAuthority('INBOUND_CREATE', 'OUTBOUND_CREATE')")
```
*Lưu ý: Phân quyền theo Authority giúp hệ thống linh hoạt hơn, khi Admin thay đổi quyền của vai trò trong Database, logic code không cần phải sửa đổi hay redeploy lại server.*

---

## 🚀 Hướng Dẫn Test API với Swagger UI (Đã Tích Hợp)

Ứng dụng đã được tích hợp **Springdoc OpenAPI (Swagger UI)** để tiện lợi cho việc chạy thử và test API.

### 1. Đường dẫn truy cập (khi server chạy local):
* **Swagger UI HTML**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
* **OpenAPI Specs Json**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### 2. Cách test API cần quyền truy cập (JWT Bearer Auth):
1. Gọi API `POST /api/auth/login` trên Swagger UI với tài khoản test (ví dụ: `admin@stockspace.com` / `Password123`).
2. Copy chuỗi `accessToken` nhận được từ Response body.
3. Nhấp nút **Authorize** màu xanh lá ở góc trên bên phải giao diện Swagger UI.
4. Dán chuỗi token vừa copy vào ô input (Hệ thống đã tự động định dạng tiền tố `Bearer`, do đó bạn **chỉ cần dán nguyên chuỗi token**, không cần ghi chữ "Bearer " phía trước).
5. Nhấp **Authorize** rồi **Close**. Lúc này bạn đã có thể gửi request test cho tất cả các API được bảo vệ bởi `@PreAuthorize`.

---

## 🐋 Hướng Dẫn Sử Dụng Docker Compose (postgres)

Do máy của bạn không cài đặt PostgreSQL trực tiếp, bạn có thể khởi động cơ sở dữ liệu qua Docker Compose bằng một lệnh duy nhất.

### 1. Chỉ chạy Database (Khuyên dùng để phát triển local từ IDE):
```bash
docker compose up -d postgres
```
* **PostgreSQL** sẽ chạy tại `localhost:5432` với cấu hình trùng khớp với [application.properties](file:///d:/StockSpace_BE/StockSpace_BE/src/main/resources/application.properties) (`username: postgres`, `password: 123456`, `database: stockspace`).

### 2. Chạy toàn bộ cả DB và ứng dụng Spring Boot trong Docker:
```bash
docker compose up -d --build
```
Lệnh này sẽ build ứng dụng Spring Boot và chạy mọi thứ trong network riêng của Docker. Ứng dụng sẽ tự động đợi database khởi động thành công (healthcheck status: `service_healthy`) rồi mới bắt đầu chạy để tránh lỗi kết nối DB.



