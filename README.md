# StockSpace Backend

Backend cho nền tảng quản lý và cho thuê kho bãi. Dự án cung cấp API cho xác thực, phân quyền, quản lý kho, hợp đồng, tồn kho, thanh toán, thông báo thời gian thực và chatbot AI.

<p align="center">
  <img src="https://img.shields.io/badge/Spring_Boot-4.0.6-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 17" />
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL" />
  <img src="https://img.shields.io/badge/Docker-Enabled-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker" />
</p>

## Tính năng chính

- Xác thực JWT, refresh-token, Google OAuth và RBAC theo role/permission.
- Quản lý kho, layout/rack/bin, booking, hợp đồng, kiểm định và nhân sự kho.
- WMS: sản phẩm/SKU, phiếu nhập-xuất, lô tồn, kiểm kê và audit trail.
- Ví, gói dịch vụ, subscription, VNPAY, rút tiền và thống kê.
- Thông báo REST + WebSocket/STOMP.
- Chatbot dùng OpenRouter, function calling, RAG và pgvector.

## Công nghệ

- Java 17, Spring Boot 4.0.6, Spring Security và Spring Data JPA.
- PostgreSQL 16 với pgvector.
- Docker Compose, Nginx (production), GitHub Actions.
- Cloudinary, VNPAY, SMTP Gmail, OpenAPI/Swagger UI và OpenRouter.

## Chạy local

### Yêu cầu

- Docker Desktop/Docker Compose để chạy PostgreSQL (hoặc một PostgreSQL 16 có pgvector sẵn có).
- Java 17 nếu chạy ứng dụng bằng Maven Wrapper. Không cần Java trên máy khi chạy toàn bộ stack bằng Docker.

### 1. Chuẩn bị cấu hình

Sao chép `.env.example` thành `.env`, sau đó điền các giá trị bí mật và tích hợp cần dùng:

```bash
cp .env.example .env
```

Trên PowerShell:

```powershell
Copy-Item .env.example .env
```

Tối thiểu nên đặt `JWT_SECRET`, thông tin PostgreSQL và các credentials của dịch vụ mà bạn bật. Danh sách biến môi trường đầy đủ nằm trong `.env.example`; không commit `.env`.

### 2. Khởi động PostgreSQL

```bash
docker compose up -d postgres
```

Docker Compose local tự nạp `docker-compose.override.yml`, vì vậy PostgreSQL được mở ở cổng `5432`.

### 3. Chạy ứng dụng

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

Hoặc chạy toàn bộ stack bằng Docker:

```bash
docker compose up --build
```

API mặc định chạy tại `http://localhost:8080`.

## API và kiểm tra sức khỏe

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health check: `http://localhost:8080/actuator/health`
- Chat đã đăng nhập: `/api/chat`
- Chat khách: `/api/chat/guest`

Để chạy toàn bộ test:

```bash
./mvnw verify
```

Trên Windows:

```powershell
.\mvnw.cmd verify
```

## Chatbot

Chatbot cần `OPENROUTER_API_KEY` và `OPENROUTER_MODEL`. Cơ sở dữ liệu sử dụng image `pgvector/pgvector:0.8.5-pg16`; schema vector được khởi tạo theo cấu hình ứng dụng. Các biến `CHATBOT_*` trong `.env.example` điều chỉnh SSE, giới hạn request, RAG và retention.

## CI/CD và production

- Pull request vào `main` chạy `./mvnw --batch-mode verify` qua `.github/workflows/ci-pr.yml`.
- Push vào `main` hoặc chạy thủ công workflow deploy sẽ gọi `deploy.sh deploy` trên VPS qua `.github/workflows/deploy-production.yml`.
- Production chạy bằng `docker-compose.yml` kết hợp `docker-compose.prod.yml`; xem `deploy.sh`, `Dockerfile` và cấu hình trong `nginx/` khi cần thay đổi hạ tầng.
- Workflow deploy dùng GitHub Environment `production` với các secrets `VPS_HOST`, `VPS_USERNAME`, `VPS_SSH_KEY_B64`, `VPS_KNOWN_HOSTS`; `VPS_PORT` là tùy chọn.
- Profile `prod` chỉ khởi động khi JWT secret hợp lệ và OpenRouter được cấu hình. JWT secret phải là Base64/Base64URL, tối thiểu 256 bit sau khi giải mã.

## Cấu trúc dự án

```text
src/main/java/fu/stockspace/stockspace_be/
├── auth/           # Xác thực, JWT, RBAC
├── admin/          # API quản trị
├── booking/        # Yêu cầu thuê kho
├── chatbot/        # OpenRouter, RAG, tool calling và SSE
├── common/         # Cấu hình, lỗi và DTO dùng chung
├── contract/       # Hợp đồng và tranh chấp
├── inspection/     # Kiểm định kho
├── notification/   # Thông báo và WebSocket
├── staff/          # Nhân sự kho
├── stats/          # Thống kê cho owner và admin
├── subscription/   # Gói dịch vụ và subscription
├── wallet/         # Ví và thanh toán
├── warehouse/      # Kho, layout và vị trí lưu trữ
└── wms/            # Sản phẩm, nhập/xuất, tồn kho và kiểm kê
```

## Lưu ý bảo mật

- Không đưa `.env`, khóa SSH, JWT secret hay thông tin thanh toán vào Git.
- Production hiện bật `SEED_DEMO_USERS=true`; phải đổi mật khẩu mặc định của các tài khoản demo ngay sau khi seed.
- Profile `prod` đã bật cookie secure; luôn triển khai production qua HTTPS.
