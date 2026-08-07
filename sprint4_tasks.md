# Danh Sách Công Việc Sprint 4 — AI Chatbot (Agentic Function Calling & RAG)

Tài liệu theo dõi toàn bộ công việc của **2 developer** cho sprint này.
Sprint 3 đã hoàn thành: Notification · WarehouseBin · WMS Phase 2 · Staff Invitation · Chatbot skeleton.

> ✅ = Hoàn thành | 🔄 = Đang làm | ⬜ = Chưa làm

---

## 🚀 Cập Nhật Thực Tế Kiến Trúc Chatbot (Production Update — Dev B Chú Ý)

1. **Chuyển từ Gemini trực tiếp ➔ OpenRouter Chat Completions API**:
   - Provider: OpenRouter với Privacy ZDR (`OPENROUTER_DATA_COLLECTION=deny`, `OPENROUTER_ZDR=true`).
   - Cấu hình qua `.env`: `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`.
2. **Hỗ trợ SSE Streaming (`text/event-stream`)**:
   - Endpoints: `POST /api/chat/stream` (User) & `POST /api/chat/guest/stream` (Guest).
   - Event types: `session`, `status`, `delta`, `ping`, `complete`, `error`.
   - Frontend dùng Fetch Streaming API, đọc chunks qua `TextDecoderStream`.
3. **Bảo mật Guest Session Token**:
   - Header: `X-Chat-Session-Token: <token>` (Lưu băm SHA-256 trong DB, TTL rolling, tự động purge sau 7 ngày).
4. **Hệ thống RAG Hybrid Search với pgvector**:
   - Thêm `SearchSystemPolicyTool` dùng pgvector (`vector(1536)` HNSW index + Cosine similarity).
   - Dynamic threshold & HNSW scan (`hnsw.iterative_scan=strict_order`).
5. **Database Migration Script**:
   - Migration idempotent: `ops/migrations/20260728_chatbot_production.sql`.
   - Chi tiết API và streaming specs xem tại: [`CHATBOT.md`](file:///d:/StockSpace_BE/StockSpace_BE/CHATBOT.md).

---

## 🏗️ Kiến Trúc — Agentic Function Calling (Enterprise Pattern)

Thay vì nhồi dữ liệu cứng vào prompt, **AI tự quyết định gọi tool nào** dựa vào ý định của user.

### Luồng xử lý

```
User gửi tin nhắn (Sync hoặc SSE Stream)
        │
        ▼
ChatbotService → xác định role từ JWT / Guest Token (GUEST / TENANT / OWNER / STAFF / ADMIN / INSPECTOR)
        │
        ▼
OpenRouterClient.chatWithTools(history, systemPrompt, userMessage, tools[role])
        │
        ▼
Model trả về:
  ├── [TEXT]          ──► Lưu DB → Return/Stream response ✅
  └── [FUNCTION_CALL] ──► execute tool → gửi kết quả lại Model → lặp (tối đa 5 vòng)
```

### Phân quyền Tool theo Role

```
GUEST       → [searchWarehouses, getWarehouseDetail, searchSystemPolicy, askLoginPrompt]
TENANT      → GUEST + [getMyContracts, getContractDetail, getMyStock, getMyWallet]
OWNER       → [getMyWarehouses, getWarehouseBookings, getRevenueSummary, getOccupancyRate]
STAFF       → [getAssignedWarehouseStock, getPendingInboundOrders, getPendingOutboundOrders]
ADMIN       → [getPlatformSummary, getMonthlyRevenue]
INSPECTOR   → [getMyAssignedInspections, getInspectionDetail]
```

> ⚠️ AI **không thể gọi tool ngoài danh sách role** của mình.
> GUEST hỏi "hợp đồng của tôi" → chỉ có `askLoginPrompt` → tự hướng dẫn đăng nhập.

---

## ⚠️ Điểm Sync Giữa 2 Dev (Trạng thái Hiện tại)

- [x] **Dev A định nghĩa `ChatTool` interface** → Dev B implement tools của mình dựa trên interface này
- [x] **Dev A hoàn thành `ChatToolRegistry`** → Dev B đăng ký tools vào registry
- [x] **Dev A hoàn thành `OpenRouterClient` & SSE Streaming** → Dev B dùng để test tools & stream
- [x] **Dev A hoàn thành RAG Policy Search (`SearchSystemPolicyTool`)**
- [ ] **Dev B expose internal methods** cần thiết cho tools tiếp theo (OWNER/STAFF/ADMIN/INSPECTOR):
  - `ContractService.getMyContracts(UUID tenantId)` → `List<ContractSummaryDto>`
  - `WalletService.getBalance(UUID userId)` → `BigDecimal`
  - `StockBatchService.getSummaryByWarehouse(UUID warehouseId)` → `StockSummaryDto`
  - `BookingService.getPendingByWarehouse(UUID warehouseId)` → `List<BookingSummaryDto>`
  - `InspectionRepository.findByInspectorId(UUID)` → danh sách inspection

---

## 🅰 DEV A — Chatbot Infrastructure · Guest · Tenant · Owner Tools

### ═══ MODULE 1: Core Infrastructure ═══

> ✅ Skeleton sẵn có: `GeminiProperties`, `ChatSession`, `ChatMessage`, `ChatSessionRepository`, `ChatMessageRepository`, `SendMessageRequest`, `ChatFilterDto`, `ChatResponse`, `ChatMessageResponse`, ErrorCodes chatbot, SecurityConfig permitAll `/api/chat/guest/**`.

#### 1.1. WebClient Config
- [x] **`WebClientConfig.java`** — `@Configuration` ✅ (tồn tại tại `chatbot/config/WebClientConfig.java`, dùng OpenRouter base URL thay Gemini)
- [x] Kiểm tra `pom.xml` đã có `spring-boot-starter-webflux` chưa, nếu chưa thêm vào ✅

#### 1.2. ChatTool Interface ✅
- [x] **`ChatTool.java`** — interface chuẩn hoàn chỉnh tại `chatbot/tool/ChatTool.java`

#### 1.3. ChatToolRegistry ✅
- [x] **`ChatToolRegistry.java`** — `@Component` hoàn chỉnh tại `chatbot/tool/ChatToolRegistry.java`
  - GUEST, TENANT tools đã đăng ký đầy đủ
  - OWNER/STAFF/ADMIN/INSPECTOR tạm dùng public tools (chờ Dev B implement)

#### 1.4. OpenRouterClient (Function Calling & SSE Streaming) ✅
- [x] **`OpenRouterClient.java`** — `@Component` hoàn chỉnh tại `chatbot/client/OpenRouterClient.java` (~38KB)
  - Hỗ trợ function calling với tool definitions
  - SSE streaming
  - Error handling: quota exceeded, provider error
- [x] **`EmbeddingClient.java`** — tại `chatbot/client/EmbeddingClient.java` (~12KB)

  > ⚠️ **Đã chuyển từ GeminiClient → OpenRouterClient** theo kiến trúc production update

#### 1.5. PromptBuilder (6 system prompts theo role) ✅
- [x] **`PromptBuilder.java`** — `@Component` hoàn chỉnh tại `chatbot/service/PromptBuilder.java`
  - Build system prompt cho tất cả 6 roles: GUEST, TENANT, OWNER, STAFF, ADMIN, INSPECTOR

#### 1.6. ChatbotService (Agentic Loop — Core Logic) ✅
- [x] **`ChatbotService.java`** — `@Service` hoàn chỉnh tại `chatbot/service/ChatbotService.java` (~819 lines)
  - Agentic loop dùng `OpenRouterClient`
  - Sync JSON (`processMessage`) và SSE Streaming
  - Guest session token (SHA-256 hash, rolling TTL)
  - Session management: `getMySessions`, `getSessionMessages`, `deleteSession`, `getGuestHistory`
- [x] **`ChatConversationStore.java`** — quản lý conversation context
- [x] **`ChatRetentionService.java`** + **`ChatRetentionScheduler.java`** — tự động purge session cũ

#### 1.7. Tools — GUEST & TENANT ✅
- [x] **`SearchWarehousesTool`** — ✅ query `WarehouseRepository` AVAILABLE, filter city/type/price/area
- [x] **`GetWarehouseDetailTool`** — ✅ query `WarehouseRepository.findById`, return detail JSON
- [x] **`AskLoginPromptTool`** — ✅ return `{"action":"PROMPT_LOGIN",...}`
- [x] **`SearchSystemPolicyTool`** — ✅ RAG hybrid search với pgvector (~23KB)
- [x] **`GetMyContractsTool`** — ✅ gọi `ContractService.getMyContractsAsTenant(userId, 0, 20)`
- [x] **`GetContractDetailTool`** — ✅ gọi `ContractService.getContractById(contractId, userId)`
- [x] **`GetMyStockTool`** — ✅ gọi `StockBatchService.getStockSummaryByWarehouse(userId, warehouseId)`
- [x] **`GetMyWalletTool`** — ✅ gọi `WalletService.getWalletInfo(userId)`

#### 1.8. DTOs bổ sung ✅
- [x] **`ChatSessionResponse.java`** — ✅ tồn tại tại `chatbot/dto/ChatSessionResponse.java`
- [x] **`ChatMessageResponse.java`** — ✅ tồn tại
- [x] **`ChatStreamEvents.java`** — ✅ SSE event types

#### 1.9. Controllers ✅
**`UserChatController.java`** — ✅ `@RequestMapping("/api/chat")`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Mô tả |
|--------|------|--------|
| `POST` | `/api/chat/send` | Gửi tin nhắn (auto-detect role từ JWT) |
| `POST` | `/api/chat/stream` | SSE Streaming |
| `GET` | `/api/chat/sessions` | Danh sách phiên hội thoại (phân trang) |
| `GET` | `/api/chat/sessions/{id}/messages` | Lịch sử tin nhắn |
| `DELETE` | `/api/chat/sessions/{id}` | Xóa mềm phiên |

**`GuestChatController.java`** — ✅ `@RequestMapping("/api/chat/guest")`, Public

| Method | Path | Mô tả |
|--------|------|--------|
| `POST` | `/api/chat/guest/send` | Gửi tin nhắn không cần đăng nhập |
| `POST` | `/api/chat/guest/stream` | SSE Streaming guest |
| `GET` | `/api/chat/guest/history` | Lịch sử chat theo `?sessionToken=...` |

---

## 🅱 DEV B — Expose Internal Methods · Owner · Staff · Admin · Inspector Tools · Stats

### ═══ MODULE 2: Expose Internal Methods (Ngày 1 — Bắt buộc) ═══

> Dev A cần các method này để implement tools. Dev B làm trước khi bắt đầu tool.

- [ ] **`ContractService`** thêm/expose: `getMyContracts(UUID tenantId)` → `List<ContractSummaryDto>`, `getContractDetail(UUID contractId, UUID userId)` → `ContractDetailDto`
- [ ] **`WalletService`** thêm/expose: `getBalance(UUID userId)` → `BigDecimal`
- [ ] **`StockBatchService`** thêm/expose: `getSummaryByWarehouse(UUID warehouseId)` → `StockSummaryDto`
- [ ] **`BookingService`** thêm/expose: `getPendingByWarehouse(UUID warehouseId)` → `List<BookingSummaryDto>`
- [ ] **DTOs nội bộ nếu chưa có:** `ContractSummaryDto`, `ContractDetailDto`, `StockSummaryDto`, `BookingSummaryDto`

---

### ═══ MODULE 3: Tools — OWNER (Dev B implement) ═══

- [ ] **`GetMyWarehousesTool`** — query `WarehouseRepository.findByOwnerId(userId)`, return list JSON
- [ ] **`GetWarehouseBookingsTool`** — params: `warehouseId` (optional), gọi `BookingService.getPendingByWarehouse(warehouseId)`
- [ ] **`GetRevenueSummaryTool`** — params: `year`, query `WalletTransaction` type `RENTAL_PAYMENT`, group by month, return JSON
- [ ] **`GetOccupancyTool`** — đếm kho RENTED / tổng kho của owner, return tỉ lệ + danh sách JSON

---

### ═══ MODULE 4: Tools — STAFF (Dev B implement) ═══

- [ ] **`GetAssignedStockTool`** — query `StockBatchRepository` kho Staff được phân công, return tồn kho JSON
- [ ] **`GetPendingInboundTool`** — query `InventoryReceiptRepository` status PENDING của kho phân công
- [ ] **`GetPendingOutboundTool`** — query outbound orders PENDING của kho phân công

---

### ═══ MODULE 5: Tools — ADMIN (Dev B implement) ═══

- [ ] **`GetPlatformSummaryTool`** — đếm tổng User/Owner/Tenant/Kho/Contract/Booking/Revenue tháng hiện tại
- [ ] **`GetMonthlyRevenueTool`** — params: `year`, revenue commission từng tháng

---

### ═══ MODULE 6: Tools — INSPECTOR (Dev B implement) ═══

- [ ] **`GetMyInspectionsTool`** — query `InspectionRepository.findByInspectorId(userId)` status PENDING/IN_PROGRESS, return JSON
- [ ] **`GetInspectionDetailTool`** — params: `inspectionId`, return chi tiết yêu cầu kiểm định

---

### ═══ MODULE 7: Báo Cáo & Thống Kê (API trực tiếp — ngoài chatbot) ═══

> Chatbot trả lời stats qua tool, nhưng frontend cũng cần API endpoint riêng cho Dashboard.

#### 7.1. Owner Stats
- [ ] **`OwnerStatsService.java`**:
  - `getRevenueSummary(UUID ownerId, int year)` → `RevenueStatsResponse`
  - `getOccupancyRate(UUID ownerId)` → `OccupancyStatsResponse`
  - `getRecentActivity(UUID ownerId)` → `List<ActivitySummary>` (10 sự kiện gần nhất)

- [ ] **`OwnerStatsController.java`** — `/api/owner/stats`, `hasRole('OWNER')`

| Method | Path | Mô tả |
|--------|------|--------|
| `GET` | `/api/owner/stats/revenue` | Doanh thu theo năm (`?year=`) |
| `GET` | `/api/owner/stats/occupancy` | Tỉ lệ lấp đầy |
| `GET` | `/api/owner/stats/activity` | Hoạt động gần đây |

#### 7.2. Admin Stats
- [ ] **`AdminStatsService.java`**:
  - `getPlatformSummary()` → `PlatformSummaryResponse`
  - `getMonthlyRevenue(int year)` → `List<MonthlyRevenueDto>`

- [ ] **`AdminStatsController.java`** — `/api/admin/stats`, `hasRole('ADMIN')`

| Method | Path | Mô tả |
|--------|------|--------|
| `GET` | `/api/admin/stats/summary` | Tổng quan nền tảng |
| `GET` | `/api/admin/stats/revenue` | Doanh thu commission theo năm |

#### 7.3. DTOs Stats
- [ ] `RevenueStatsResponse.java` — `int year`, `List<MonthlyRevenueDto>`, `BigDecimal totalRevenue`
- [ ] `MonthlyRevenueDto.java` — `int month`, `BigDecimal revenue`
- [ ] `OccupancyStatsResponse.java` — `int total`, `int rented`, `double occupancyRate`, `List<WarehouseStatusDto>`
- [ ] `PlatformSummaryResponse.java` — users, warehouses, bookings, contracts, monthlyCommission

---

## 🔗 ErrorCode Cần Thêm

> ✅ Đã có: `CHAT_SESSION_NOT_FOUND`, `CHAT_SESSION_ACCESS_DENIED`, `GEMINI_API_ERROR`, `GEMINI_API_QUOTA_EXCEEDED`

- [x] `CHAT_TOOL_EXECUTION_ERROR` — ✅ đã có trong `ErrorCode.java`

---

## 📊 Tóm Tắt Phân Công

| # | Nhiệm vụ | Dev | Ưu tiên | Trạng thái |
|---|----------|-----|---------|-----------|
| 1 | `WebClientConfig` + `OpenRouterClient` (OpenRouter API + Privacy ZDR) | **Dev A** | 🔴 | ✅ |
| 2 | `ChatTool` interface | **Dev A** | 🔴 | ✅ |
| 3 | `ChatToolRegistry` | **Dev A** | 🔴 | ✅ |
| 4 | OpenRouter Client (Function Calling & Tool Execution) | **Dev A** | 🔴 | ✅ |
| 5 | `PromptBuilder` (Prompts theo 6 roles) | **Dev A** | 🟡 | ✅ |
| 6 | `ChatbotService` (Agentic loop + SSE Streaming) | **Dev A** | 🟡 | ✅ |
| 7 | Tools: GUEST (`searchWarehouses`, `getWarehouseDetail`, `searchSystemPolicy`, `askLoginPrompt`) | **Dev A** | 🟡 | ✅ |
| 8 | Tools: TENANT (`getMyContracts`, `getContractDetail`, `getMyStock`, `getMyWallet`) | **Dev A** | 🟡 | ✅ |
| 9 | Guest Session Token (`X-Chat-Session-Token`, SHA-256 hash, rolling TTL) | **Dev A** | 🟢 | ✅ |
| 10 | User/Guest Chat Controllers (Sync JSON & SSE Stream) | **Dev A** | 🟢 | ✅ |
| 11 | RAG Hybrid Search with pgvector (`ops/migrations/20260728_chatbot_production.sql`) | **Dev A** | 🔴 | ✅ |
| 12 | Expose internal methods (Contract/Wallet/Stock/Booking/Inspection) | **Dev B** | 🔴 | ✅ |
| 13 | Tools: OWNER (`getMyWarehouses`, `getWarehouseBookings`, `getRevenueSummary`, `getOccupancyRate`) | **Dev B** | 🟡 | ✅ |
| 14 | Tools: STAFF (`getAssignedWarehouseStock`, `getPendingInboundOrders`, `getPendingOutboundOrders`) | **Dev B** | 🟡 | ✅ |
| 15 | Tools: ADMIN (`getPlatformSummary`, `getMonthlyRevenue`) | **Dev B** | 🟡 | ✅ |
| 16 | Tools: INSPECTOR (`getMyAssignedInspections`, `getInspectionDetail`) | **Dev B** | 🟡 | ✅ |
| 17 | Owner Stats Service + Controller + DTOs | **Dev B** | 🟢 | ✅ |
| 18 | Admin Stats Service + Controller + DTOs | **Dev B** | 🟢 | ✅ |

