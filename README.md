# 🏢 StockSpace — Hệ Thống Quản Lý Kho Bãi Thông Minh (Smart Warehouse Platform)

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-4.0.6-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Boot Badge"/>
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java Badge"/>
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL Badge"/>
  <img src="https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker Badge"/>
  <img src="https://img.shields.io/badge/JWT-Protected-black?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JWT Badge"/>
</p>

---

## 📖 Giới Thiệu Dự Án
**StockSpace** là một nền tảng quản lý và vận hành kho bãi thông minh hiện đại. Dự án được thiết kế chuyên biệt để giải quyết các bài toán về tối ưu hóa không gian lưu trữ, tự động hóa quy trình nhập/xuất kho, kiểm tra thanh tra chất lượng và kết nối hiệu quả giữa **Chủ kho (Warehouse Owner)** và **Người thuê kho (Tenant)**.

Mã nguồn Backend này được phát triển trên nền tảng **Spring Boot** kết hợp cùng cơ chế bảo mật nghiêm ngặt của **Spring Security**, kiến trúc phân quyền động **Full RBAC (Role-Based Access Control)** và giải pháp đóng gói **Docker** hoàn chỉnh.

---

## 🌟 Bản Đồ Tính Năng Hệ Thống (Platform Features Map)

StockSpace được xây dựng với mục tiêu trở thành một hệ sinh thái quản lý kho toàn diện, bao gồm cả các nghiệp vụ quản lý kinh doanh lẫn hạ tầng kỹ thuật bảo mật cao.

### 🏢 A. Các Phân Hệ Nghiệp Vụ Chính (Business Modules)

1. **Quản Lý Kho Bãi (Warehouse Management)**
   * **Đăng ký kho**: Cho phép Chủ kho (Owner) tạo mới, chỉnh sửa thông tin kho (vị trí GPS, kích thước, hình ảnh, sơ đồ mặt bằng, bảng giá, trang thiết bị).
   * **Tìm kiếm & Lọc thông minh**: Cho phép Người thuê (Tenant) tìm kho bãi trống theo khoảng cách, diện tích, khoảng giá, và xếp hạng chất lượng.
   * **Trạng thái kho**: Tự động cập nhật trạng thái kho bãi (Đang trống, Đã thuê, Đang bảo trì).

2. **Quản Lý Giao Dịch & Thuê Kho (Rental & Lease Management)**
   * **Yêu cầu thuê**: Tenant có thể tạo và gửi yêu cầu thuê kho kèm các điều khoản hợp đồng mong muốn.
   * **Phê duyệt hợp đồng**: Owner nhận thông báo, xem hồ sơ Tenant và thực hiện duyệt/từ chối yêu cầu thuê.
   * **Quản lý lịch sử**: Theo dõi thời hạn thuê, chu kỳ thanh toán và tự động cảnh báo gia hạn hợp đồng.

3. **Quản Lý Tồn Kho Hàng Hóa (Inventory Management)**
   * **Danh mục sản phẩm**: Quản lý thông tin hàng hóa lưu trữ (loại hàng, mã SKU/Barcode, hạn sử dụng, điều kiện bảo quản đặc biệt như đông lạnh, dễ vỡ).
   * **Cảnh báo tồn kho**: Thiết lập định mức an toàn và tự động gửi cảnh báo khi số lượng hàng trong kho xuống dưới mức cho phép.

4. **Quản Lý Phiếu Nhập & Xuất Kho (Inbound & Outbound Management)**
   * **Tạo phiếu**: Cho phép Tenant tạo lịch nhập kho (Inbound) và xuất kho (Outbound) trước khi hàng cập bến.
   * **Thực thi & Đối soát**: Nhân viên kho (Staff) quét mã, kiểm đếm số lượng thực tế tại chỗ và cập nhật trực tiếp vào số lượng tồn kho hệ thống.

5. **Thanh Tra & Đánh Giá Chất Lượng (Quality Inspection)**
   * **Lịch trình thanh tra**: Thanh tra viên (Inspector) lập lịch kiểm tra an toàn cháy nổ, vệ sinh, và kết cấu kho định kỳ.
   * **Báo cáo chất lượng**: Lập biên bản điện tử và chấm điểm chất lượng kho. Báo cáo này hiển thị công khai để tăng uy tín của Chủ kho đối với Tenant.

6. **Phân Quyền Nhân Sự Nội Bộ (Staff Delegation)**
   * Tenant có thể tạo tài khoản và phân quyền cho Nhân viên (Staff) của mình để thực thi việc quản lý hàng hóa và quét mã nhập xuất tại kho đã thuê.

7. **Quói Dịch Vụ & Thanh Toán (Subscription & Billing)**
   * Quản lý các gói dịch vụ sử dụng nền tảng StockSpace của các đối tác (Owner, Tenant) và tích hợp cổng thanh toán để nạp tiền/thanh toán hóa đơn hàng tháng.

---

### ⚙️ B. Phân Hệ Kỹ Thuật & Bảo Mật (Technical & Infrastructure Features)

1. **🔐 Xác Thực Cấp Cao (JWT + HttpOnly Cookie)**
   * **Dual-Token Strategy**: Sử dụng `accessToken` (lưu trữ ngắn hạn trên memory phía client) và `refreshToken` (lưu trữ dài hạn trong cơ sở dữ liệu và chuyển giao qua **HttpOnly & Secure Cookie** phía client). Ngăn chặn hoàn toàn các cuộc tấn công XSS.
   * **Refresh Token Rotation (RTR)**: Tự động hủy token cũ và phát hành token mới trong mỗi phiên làm việc. Phát hiện và ngăn chặn lập tức hành vi đánh cắp session.
   * **Logout Toàn Diện**: Hỗ trợ đăng xuất thiết bị hiện tại (xóa session tương ứng) và đăng xuất khỏi toàn bộ thiết bị (xóa sạch các phiên đăng nhập đang hoạt động trong DB).

2. **🔀 Phân Quyền Động Đa Tầng (Full RBAC)**
   * Không sử dụng phân quyền tĩnh cứng trong code. Hệ thống sở hữu 5 bảng cơ sở dữ liệu liên kết động:
     $$\text{Users} \longleftrightarrow \text{Roles} \longleftrightarrow \text{Permissions}$$
   * **Hỗ trợ gán nhiều vai trò (Multi-Role)** cho một người dùng.
   * Tích hợp cơ chế kiểm tra quyền hạn chi tiết (**Permission-based authorization**) qua `@PreAuthorize("hasAuthority('PERMISSION_NAME')")`. Admin có thể thay đổi toàn bộ quyền hạn của một Role ngay trên giao diện mà không cần chỉnh sửa một dòng code hay khởi động lại server.

3. **🚀 Tự Động Khởi Tạo Dữ Liệu (Auto-Seeding)**
   * Tích hợp `DataInitializer` tự động khởi chạy khi deploy ứng dụng:
     * Seed sẵn **18 quyền hạn nghiệp vụ** (Warehouse, Rental Request, Inspection, Inventory, Inbound, Outbound, Staff, Package...).
     * Tạo sẵn **5 vai trò mặc định** kèm cấu hình phân quyền tiêu chuẩn.
     * Tạo sẵn **5 tài khoản kiểm thử đại diện** ứng với từng vai trò.

4. **🔌 Tài Liệu API Tương Tác Trực Quan (Swagger UI)**
   * Tích hợp **Springdoc OpenAPI v2** hỗ trợ giao diện thử nghiệm API tương tác.
   * Cấu hình sẵn cơ chế **JWT Bearer Authentication**, cho phép bạn bấm nút **Authorize** dán token trực tiếp để test các endpoint yêu cầu quyền truy cập bảo mật.

---

## 🛠️ Công Nghệ Sử Dụng (Technology Stack)
* **Core Framework**: Spring Boot 4.0.6 (Spring Framework 6.x)
* **Language**: Java 17 (OpenJDK)
* **Security & Authentication**: Spring Security 6.x & JJWT (Java JWT) 0.12.6
* **Database**: PostgreSQL 16
* **ORM & Database Seeding**: Spring Data JPA / Hibernate 7
* **API Documentation**: Springdoc OpenAPI WebMVC UI 2.8.5
* **DevOps**: Docker & Docker Compose
* **Utilities**: Lombok, Jakarta Validation

---


## 🚀 Hướng Dẫn Khởi Chạy Dự Án (Quick Start)

### Yêu Cầu Hệ Thống:
* Đã cài đặt **Docker** và **Docker Desktop**.
* *(Không cần cài đặt Java hay PostgreSQL trực tiếp trên máy).*

### Bước 1: Pull mã nguồn về máy
```bash
git clone <url-repo-cua-ban>
cd StockSpace_BE
```

### Bước 2: Khởi chạy cơ sở dữ liệu qua Docker Compose
Mở terminal tại thư mục gốc dự án và chạy:
```bash
docker compose up -d postgres
```
*Lệnh này sẽ tải image PostgreSQL và chạy ngầm một container DB ở port `5432` trên máy của bạn.*

### Bước 3: Chạy ứng dụng Spring Boot
Bạn chỉ cần mở dự án bằng IntelliJ IDEA / Eclipse hoặc chạy lệnh Maven sau ở local:
```bash
./mvnw spring-boot:run
```
*(Nếu muốn chạy cả ứng dụng lẫn database hoàn toàn trong môi trường Docker, hãy chạy: `docker compose up -d --build`)*

---

## 🎯 Danh Sách Tài Khoản Thử Nghiệm (Default Seeded Users)
Tất cả các tài khoản mặc định đều sử dụng chung mật khẩu: **`Password123`**

| Email | Vai Trò (Role) | Chức Năng Chính |
|-------|----------------|-----------------|
| `admin@stockspace.com` | **ROLE_ADMIN** | Toàn quyền kiểm soát hệ thống, quản lý User, Role, Permission. |
| `owner@stockspace.com` | **ROLE_OWNER** | Quản lý thông tin kho bãi của mình, duyệt yêu cầu thuê, xem thanh tra. |
| `tenant@stockspace.com` | **ROLE_TENANT** | Người thuê kho, quản lý hàng hóa, tạo phiếu nhập/xuất, mua gói dịch vụ. |
| `staff@stockspace.com` | **ROLE_STAFF** | Nhân viên kho, quản lý tồn kho, nhập/xuất kho thực tế. |
| `inspector@stockspace.com` | **ROLE_INSPECTOR**| Thanh tra, đánh giá chất lượng kho bãi, phê duyệt biên bản thanh tra. |

---

## 🧪 Hướng Dẫn Test API với Swagger UI

1. Đảm bảo ứng dụng đang chạy ở local. Truy cập vào đường dẫn:
   👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**
2. Tìm đến nhóm API `/api/auth/login`, bấm **Try it out**, nhập tài khoản test (ví dụ: `admin@stockspace.com` / `Password123`) và thực hiện gửi request.
3. Từ response trả về, copy chuỗi `accessToken`.
4. Cuộn lên trên cùng bên phải giao diện Swagger, nhấp nút **Authorize** màu xanh lá.
5. Dán chuỗi token vừa copy vào ô input (Không cần gõ tiền tố "Bearer", hệ thống đã tự config) rồi nhấp **Authorize** -> **Close**.
6. Bây giờ, bạn có thể thực hiện test mọi API nghiệp vụ có gắn phân quyền bảo vệ của hệ thống.

---

## 📁 Cấu Trúc Các Package Chính

```
src/main/java/fu/stockspace/stockspace_be/
│
├── auth/
│   ├── controller/
│   │   └── AuthController.java          # Endpoint đăng ký, đăng nhập, refresh, logout
│   ├── dto/
│   │   ├── LoginRequest/Response.java   # DTO trao đổi dữ liệu đăng nhập
│   │   └── RegisterRequest.java         # DTO đăng ký tài khoản mới
│   ├── entity/
│   │   ├── User.java                    # Base User Account kế thừa UserDetails
│   │   ├── Role.java                    # Bảng quản trị vai trò động
│   │   ├── Permission.java              # Bảng quản trị quyền hạn động
│   │   ├── RoleType.java                # Enum lưu định danh role hệ thống
│   │   └─- RefreshToken.java            # Phiên làm việc bảo mật của user
│   ├── repository/
│   │   ├── UserRepository.java          # Thao tác dữ liệu User
│   │   ├── RoleRepository.java          # Thao tác dữ liệu Role
│   │   └── PermissionRepository.java    # Thao tác dữ liệu Permission
│   ├── security/
│   │   ├── SecurityConfig.java          # Cấu hình Spring SecurityFilterChain, BCrypt
│   │   ├── JwtAuthFilter.java           # Filter chặn và giải mã Bearer Token mỗi request
│   │   └── JwtUtil.java                 # Bộ helper sinh/validate và đọc Claims JWT
│   └── service/
│       ├── AuthService.java             # Logic cốt lõi đăng nhập, đăng ký, rotation
│       └── RefreshTokenService.java     # Logic tạo/hủy/quay vòng refresh token cookies
│
└── common/
    ├── config/
    │   └── OpenApiConfig.java           # Cấu hình Swagger hỗ trợ JWT Auth Lock
    ├── dto/
    │   └── ApiResponse.java             # Standard JSON format phản hồi cho client
    ├── exception/
    │   └── GlobalExceptionHandler.java  # Bắt lỗi toàn hệ thống và map sang ApiResponse
    └── DataInitializer.java             # Khởi tạo seed dữ liệu mẫu khi start app
```
