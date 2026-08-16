# 04. Slides and Demo Script

## Suggested slide deck

Use this content for a 10-12 minute presentation. Keep each slide visual; use the ordered script below as the speaker guide.

| Slide | Title | Content |
|---:|---|---|
| 1 | StockSpace Platform | Project title, course, team members, supervisor, date |
| 2 | Problem and objective | Connect warehouse owners, tenants, staff, inspectors, and administrators in one platform; digitize leasing and warehouse operations |
| 3 | Users and permissions | Guest, Tenant, Warehouse Owner, Staff, Inspector, Admin; show the updated use-case diagram from Report 3 |
| 4 | Core business flow | Search warehouse -> booking and deposit -> owner approval -> rental contract -> inventory receipt -> stock/audit |
| 5 | System architecture | Frontend, Spring Boot REST API, PostgreSQL + pgvector, Docker, external VNPAY/Cloudinary/OpenRouter integrations |
| 6 | Key modules | Authentication/RBAC, warehouse and booking, contracts and disputes, WMS, wallet/subscription, notification, inspection, admin |
| 7 | Data and security | JWT + refresh token, RBAC permissions, validation, audit transactions, demo roles and seeded data |
| 8 | Implemented API evidence | Swagger UI screenshot and selected endpoints for the main business flow |
| 9 | Live demo overview | Show the role hand-off across the ordered demo steps |
| 10 | Results | Completed use cases, database initialization, and representative API evidence |
| 11 | Limitations and future work | External payment credentials, production monitoring, notification delivery, expanded reports |
| 12 | Closing | Repository link, thank you, questions |

## Live demo preconditions

1. Start the backend and PostgreSQL using `02_SOFTWARE_PACKAGE.md`.
2. Confirm Swagger UI and the health endpoint work.
3. Set `SEED_DEMO_USERS=true` before the first application start.
4. Keep browser sessions or Swagger authorizations ready for Tenant, Warehouse Owner, Staff, Inspector, and Admin.
5. The seed creates `Demo Central Warehouse` (`AVAILABLE`), `Demo North Warehouse` (`PENDING_APPROVAL`), `Demo Consumer Goods`, and two demo SKUs for the Tenant account.

## Ordered demo script

The demo prioritizes the connected business flow. Do not try to live-demo every registered use case; show remaining functions through the UI, Swagger, or prepared screenshots.

| Order | Registered use case(s) | Account | Demonstration action | Expected evidence |
|---:|---|---|---|---|
| 1 | UC-03, UC-04, UC-05 | Guest | Search and filter public warehouses, then open warehouse details and layout | Filtered list and detail response |
| 2 | UC-08, UC-09 | Guest | Ask the chatbot for a warehouse/package question; view service packages | Chat response and package list |
| 3 | UC-02, UC-12 | Tenant | Log in and open current notifications | Authenticated Tenant session and notification list |
| 4 | UC-34 | Warehouse Owner | Open and update the seeded warehouse address, capacity, price, or type | Warehouse management evidence |
| 5 | UC-53 | Admin | Verify the seeded `Demo North Warehouse` | Warehouse becomes available for public search |
| 6 | UC-19 (includes UC-20) | Tenant | Submit a lease/booking request and show the wallet/deposit result | Booking request and resulting wallet transaction |
| 7 | UC-32, UC-33 | Warehouse Owner | Open received booking requests and approve the request | Booking accepted and warehouse/contract state changes |
| 8 | UC-30, UC-35, UC-21 | Warehouse Owner -> Tenant | Owner submits the online contract; Tenant confirms it | Contract confirmation status |
| 9 | UC-23, UC-24, UC-25, UC-17, UC-18 | Tenant -> Staff | Create a category/SKU, invite and assign Staff, create an inbound receipt, then open stock and transaction data | SKU, staff assignment, receipt, stock batch, and inventory transaction |
| 10 | UC-36, UC-50, UC-38, UC-40, UC-39 | Warehouse Owner -> Admin -> Inspector | Owner requests an on-site inspection, Admin assigns Inspector, Inspector inspects and submits a report | Inspection workflow and report status |
| 11 | UC-15, UC-52 | Tenant -> Admin | Tenant submits a withdrawal request; Admin approves/rejects it | Withdrawal decision and wallet transaction |
| 12 | UC-51, UC-48, UC-49 | Admin | Finish with the dashboard, all transactions, and subscriptions | Admin monitoring evidence |

## Actor-specific demonstration priority

| Actor | Demo first | Show by screenshot/Swagger if time is limited |
|---|---|---|
| Guest | Search/filter, warehouse list/details | Register, forgot/reset password, chatbot, service packages |
| Tenant | Login, submit lease request, confirm contract, product/SKU, staff assignment | Profile, notifications, top-up, withdrawal, subscriptions, own booking management |
| Warehouse Owner | Manage warehouse, manage received bookings, approve/reject booking, submit contract, request inspection | Revenue, dispute-ticket flow |
| Staff | Execute receipts, view inventory/stock transactions | - |
| Inspector | View assignment, inspect warehouse, submit report | Settle deposit / resolve dispute |
| Admin | Approve warehouse, assign inspector, approve/reject withdrawal, reports | Users/roles, packages, configuration, policies, disputes, all transactions/subscriptions |

## Fallback plan if a third-party service is unavailable

- Use Swagger UI for all internal API use cases.
- Do not demonstrate a real VNPAY payment unless sandbox credentials are configured; demonstrate the wallet/deposit transaction instead.
- Do not demonstrate email delivery or Google login without test credentials; show the implemented endpoint in Swagger and continue with local-login accounts.
- Do not rely on a chatbot answer for a core business-flow step; it is an enhancement, not the main proof of the WMS workflow.

## Evidence to capture before the presentation

- Screenshot of successful logins for the demo roles.
- Swagger UI and health-check screenshot.
- Warehouse approval, booking approval, contract confirmation, inbound receipt, and inspection report screenshots.
- Admin dashboard/transaction/subscription screenshots.
- Final repository URL and current commit hash.
