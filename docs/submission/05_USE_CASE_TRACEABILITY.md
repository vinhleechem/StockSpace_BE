# 05. Use-Case Traceability Table

## Basis and counting rule

This table follows the updated Report 3 use-case diagram stored at `docs/submission/assets/usecase.drawio.png`.

The diagram has **53 use-case occurrences**. `Manage Booking Requests` appears twice because the Tenant manages their own requests while the Warehouse Owner manages received requests; these are documented as separate actor-specific use cases. Included/extended use cases remain listed so that the table maps every ellipse in the diagram.

**Transaction count** is the number of API transactions in the normal (happy-path) scenario. `Partial` means the backend does not fully expose the behavior for the actor shown in the diagram.

| ID | Registered use case | Main actor | Implementation evidence | Status | Transactions |
|---|---|---|---|---|---:|
| UC-01 | Register Account | Guest | `POST /api/auth/register` | Complete | 1 |
| UC-02 | Login | Guest / unauthenticated account holder | `POST /api/auth/login` | Complete | 1 |
| UC-03 | Search & Filter | Guest | `GET /api/warehouses` with filter parameters | Complete | 1 |
| UC-04 | View Warehouse List | Guest | `GET /api/warehouses` | Complete | 1 |
| UC-05 | View Warehouse Details | Guest | `GET /api/warehouses/{id}` and `/layout` | Complete | 1 |
| UC-06 | Forgot / Reset Password | Guest / unauthenticated account holder | `POST /api/auth/forgot-password`, `/reset-password` | Complete; SMTP is required for real email delivery | 2 |
| UC-07 | Send Reset Email | System / mail service | Included in the forgot-password flow through `EmailService` | Complete; requires SMTP configuration | 1 |
| UC-08 | Chat with AI | Guest / Registered User | `/api/chat/guest/*`, `/api/chat/*` | Complete | 1 |
| UC-09 | View Service Packages | Guest | `GET /api/packages` and `GET /api/packages/{id}` | Complete | 1 |
| UC-10 | Logout | Registered User | `POST /api/auth/logout`, `/logout-all` | Complete | 1 |
| UC-11 | Manage Profile | Registered User | `GET /api/auth/me` | Partial; current API exposes profile retrieval, not self-service update | 1 |
| UC-12 | View Notifications | Registered User | `GET /api/notifications`, `/unread-count` | Complete | 1 |
| UC-13 | Top up Wallet | Registered User | `POST /api/wallet/top-up` with VNPAY callback/IPN flow | Complete; sandbox credentials are required for a live payment | 2 |
| UC-14 | Process Payment | Payment Gateway | Included in wallet top-up through VNPAY callback/IPN endpoints | Complete | 1 |
| UC-15 | Request Withdrawal | Registered User | `POST /api/wallet/withdraw` | Complete | 1 |
| UC-16 | View Transaction History | Registered User | `GET /api/wallet/transactions` | Complete | 1 |
| UC-17 | Execute Inbound / Outbound Receipts | Staff | `POST /api/tenant/inventory/receipts`, receipt approval/rejection endpoints | Complete | 2 |
| UC-18 | View Inventory and Stock Transactions | Staff | `/api/tenant/inventory/stock/*` and receipt/transaction endpoints | Complete | 2 |
| UC-19 | Submit Lease Request | Tenant | `POST /api/tenant/bookings` | Complete | 1 |
| UC-20 | Pay Deposit from Wallet | Tenant | Deposit processing inside the booking workflow | Complete | 1 |
| UC-21 | Confirm Online Contract | Tenant | `POST /api/contracts/{id}/tenant-confirm` | Complete | 1 |
| UC-22 | View Rented Warehouses and Layout | Tenant | `/api/tenant/warehouses/my-warehouses`, `/{warehouseId}/layout` | Complete | 2 |
| UC-23 | Manage Product Categories and SKUs | Tenant | `/api/tenant/products/categories`, `/skus`, `/uoms` | Complete | 4 |
| UC-24 | Manage Staff | Tenant | `/api/tenant/staffs/invite`, list, remove | Complete | 3 |
| UC-25 | Assign / Revoke Staff Warehouse Assignment | Tenant | `/api/tenant/staffs/{staffUserId}/warehouses`, assignment delete | Complete | 3 |
| UC-26 | View Inventory Dashboard | Tenant | `/api/tenant/inventory/stock/overview`, `/summary` | Complete | 2 |
| UC-27 | Manage Booking Requests (own requests) | Tenant | `GET` and `DELETE /api/tenant/bookings` | Complete | 2 |
| UC-28 | Purchase / Renew / Upgrade Service Package | Tenant | `POST /api/tenant/subscriptions`, preview and active-subscription endpoints | Complete | 1 |
| UC-29 | Debit Wallet | System | Included in package purchase and booking/deposit services | Complete | 1 |
| UC-30 | Manage Rental Contract | Warehouse Owner | `/api/contracts` list/detail, cancellation, and response endpoints | Complete | 4 |
| UC-31 | Create Dispute Ticket | Warehouse Owner | `POST /api/disputes`, `GET /api/disputes/mine` | Complete | 2 |
| UC-32 | Manage Booking Requests (received requests) | Warehouse Owner | `GET /api/owner/bookings` | Complete | 1 |
| UC-33 | Approve / Reject Booking Request | Warehouse Owner | `PATCH /api/owner/bookings/{id}/approve`, `/{id}/reject` | Complete | 1 |
| UC-34 | Manage Warehouse | Warehouse Owner | `/api/owner/warehouses` create, list, update, status, delete | Complete | 5 |
| UC-35 | Submit Online Contract | Warehouse Owner | `POST /api/contracts/{id}/submit-online` | Complete | 1 |
| UC-36 | Request On-site Inspection | Warehouse Owner | `POST /api/owner/inspections` | Complete | 1 |
| UC-37 | View Revenue | Warehouse Owner | `GET /api/owner/stats/revenue` | Complete | 1 |
| UC-38 | View Assigned Inspection Requests | Inspector | `GET /api/inspector/inspections` | Complete | 1 |
| UC-39 | Submit Inspection Report | Inspector | `POST /api/inspector/inspections/{id}/report` | Complete | 1 |
| UC-40 | Inspect Warehouse | Inspector | Inspection assignment/status workflow | Complete | 1 |
| UC-41 | Settle Deposit | Inspector / Admin | Included in `POST /api/admin/disputes/{id}/resolve` | Complete through the Admin resolution flow | 1 |
| UC-42 | Resolve Disputes | Inspector / Admin | `GET, POST /api/admin/disputes/*` | Partial for Inspector; the implemented endpoint is administered through the Admin dispute API | 2 |
| UC-43 | Manage Users / Roles / Permissions | Admin | `/api/admin/users`, `/roles`, `/permissions` | Complete | 6 |
| UC-44 | Manage Dispute | Admin | `GET /api/admin/disputes`, `POST /{id}/resolve` | Complete | 2 |
| UC-45 | Manage Service Packages | Admin | `POST, PUT, DELETE /api/admin/packages` | Complete | 4 |
| UC-46 | Manage System Configuration | Admin | `GET, PUT /api/admin/configs/*` | Complete | 2 |
| UC-47 | Manage Policies | Admin | `POST, GET /api/admin/system-policies` | Complete | 2 |
| UC-48 | View All Transactions | Admin | `GET /api/admin/transactions` | Complete | 1 |
| UC-49 | View All Subscriptions | Admin | `GET /api/admin/subscriptions` | Complete | 1 |
| UC-50 | Assign Inspector | Admin | `POST /api/admin/inspections/{id}/assign` | Complete | 1 |
| UC-51 | View Reports / Dashboard | Admin | `/api/admin/stats/summary`, `/revenue` | Complete | 2 |
| UC-52 | Approve / Reject Withdrawal Requests | Admin | `GET /api/admin/withdrawals`, `PATCH /{id}/approve`, `/{id}/reject` | Complete | 3 |
| UC-53 | Approve Warehouse | Admin | `/api/admin/warehouses`, `/{id}/verify`, `/{id}/reject` | Complete | 3 |

## Summary for the presentation

- Total use-case occurrences in the updated Report 3 diagram: **53**.
- Complete: **51**.
- Partial: **2** (`UC-11 Manage Profile`, `UC-42 Resolve Disputes for Inspector`).
- The diagram represents **45 primary use cases** plus 8 included/extended subflows. The traceability table keeps all 53 diagram occurrences for one-to-one evidence.

## Verification procedure

1. Start the project with demo users enabled, as described in `02_SOFTWARE_PACKAGE.md`.
2. Log in as the actor listed in the table when authentication is required.
3. Execute the endpoint(s) under **Implementation evidence** from the frontend or Swagger UI.
4. Capture the response/status change as evidence for the corresponding row.
5. Use the ordered flow in `04_SLIDES_AND_DEMO_SCRIPT.md` to avoid creating records in the wrong state.
