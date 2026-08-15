# 02. Runnable Software Package

## Package contents

Submit the repository root as `StockSpace_BE` together with this document. The package contains the backend source code, Docker configuration, database schema initialization, and an idempotent demo-data initializer.

```text
StockSpace_BE/
├── src/                         Spring Boot source code and tests
├── ops/migrations/              PostgreSQL migration scripts
├── nginx/                       Nginx configuration for deployment
├── docker-compose.yml           Application and PostgreSQL services
├── docker-compose.override.yml  Local development profile
├── Dockerfile
├── .env.example                 Environment-variable template
├── README.md                    Project overview
└── docs/submission/             Submission instructions and demo material
```

Do not include a real `.env`, SSH key, payment secret, or production database dump in the submission archive.

## Prerequisites

- Docker Desktop with Docker Compose v2
- Java 17 (only needed when starting the application with Maven Wrapper)
- Internet access only for the first Docker image and Maven dependency download

## Local installation on Windows

1. Copy the environment template.

   ```powershell
   Copy-Item .env.example .env
   ```

2. Open `.env` and set at least the following development values.

   ```properties
   SPRING_PROFILES_ACTIVE=dev
   DB_NAME=stockspace
   DB_USERNAME=postgres
   DB_PASSWORD=123456
   JWT_SECRET=<a Base64 or Base64URL secret of at least 256 bits>
   SEED_DEMO_USERS=true
   ```

   External credentials for Cloudinary, Google OAuth, VNPAY, SMTP, and OpenRouter are optional for the basic local demo. Their related integrations should simply not be selected during the demo if credentials are unavailable.

3. Start PostgreSQL with pgvector.

   ```powershell
   docker compose up -d postgres
   ```

4. Start the backend in a second PowerShell window.

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   Alternatively, start the complete Docker stack.

   ```powershell
   docker compose up --build
   ```

5. Verify that the service is running.

   - Swagger UI: `http://localhost:8080/swagger-ui/index.html`
   - Health check: `http://localhost:8080/actuator/health`

6. Before submission, verify that the source and tests compile.

   ```powershell
   .\mvnw.cmd verify
   ```

## Demo database data

On a fresh database, the application initializes roles, permissions, default system policy, service packages, system configuration, units of measure, and chatbot knowledge. When `SEED_DEMO_USERS=true`, it also creates the following test users and wallets. The initializer is idempotent, so restarting the application does not duplicate these records.

| Role | Email | Password | Initial wallet balance | Main demo purpose |
|---|---|---|---:|---|
| Administrator | `admin@stockspace.com` | `Password123` | 0 VND | Approvals, configurations, users, reports |
| Warehouse Owner | `owner@stockspace.com` | `Password123` | 200,000,000 VND | Warehouse, booking, contract, revenue |
| Tenant | `tenant@stockspace.com` | `Password123` | 100,000,000 VND | Booking, products, inventory, subscription |
| Staff | `staff@stockspace.com` | `Password123` | 0 VND | Inbound/outbound receipts and stock operations |
| Inspector | `inspector@stockspace.com` | `Password123` | 0 VND | Warehouse inspections and reports |

The initial dataset also includes the Basic and Advanced service packages, a warehouse-posting fee package, system units of measure, an active system policy, two demo warehouses, one tenant product category, and two demo SKUs. `Demo Central Warehouse` starts as `AVAILABLE` for search/booking demonstrations; `Demo North Warehouse` starts as `PENDING_APPROVAL` for the Admin approval demonstration. Create the booking, receipt, and inspection records live so the evaluator can see their state changes.

## Resetting only a local demo database

This command deletes the local Docker database volume. Use it only when resetting your own computer, never against a shared or production environment.

```powershell
docker compose down -v
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

## Submission checklist

- [ ] `.env` is excluded and `.env.example` is included.
- [ ] `SEED_DEMO_USERS=true` is set in the evaluator's local `.env`.
- [ ] Swagger UI and `/actuator/health` open successfully.
- [ ] All five test accounts can log in with the stated roles.
- [ ] Database contains seeded roles, users, wallets, packages, policy, units of measure, and chatbot knowledge.
- [ ] `mvnw verify` passes before compressing the package.
