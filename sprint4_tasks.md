# Danh Sách Công Việc Sprint 4 — AI Chatbot (Agentic Function Calling)

Tài liệu theo dõi toàn bộ công việc của **2 developer** cho sprint này.
Sprint 3 đã hoàn thành: Notification · WarehouseBin · WMS Phase 2 · Staff Invitation · Chatbot skeleton.

> ✅ = Hoàn thành | 🔄 = Đang làm | ⬜ = Chưa làm

---

## 🏗️ Kiến Trúc — Agentic Function Calling (Enterprise Pattern)

Thay vì nhồi dữ liệu cứng vào prompt, **AI tự quyết định gọi tool nào** dựa vào ý định của user.

### Luồng xử lý

```
User gửi tin nhắn
        │
        ▼
ChatbotService → xác định role từ JWT (GUEST / TENANT / OWNER / STAFF / ADMIN / INSPECTOR)
        │
        ▼
GeminiClient.chatWithTools(history, systemPrompt, userMessage, tools[role])
        │
        ▼
Gemini trả về:
  ├── [TEXT]          ──► Lưu DB → Return response ✅
  └── [FUNCTION_CALL] ──► execute tool → gửi kết quả lại Gemini → lặp (tối đa 5 vòng)
```

### Phân quyền Tool theo Role

```
GUEST       → [searchWarehouses, getWarehouseDetail, askLoginPrompt]
TENANT      → GUEST + [getMyContracts, getContractDetail, getMyStockSummary, getMyWallet]
OWNER       → [getMyWarehouses, getWarehouseBookings, getRevenueSummary, getOccupancyRate]
STAFF       → [getAssignedWarehouseStock, getPendingInboundOrders, getPendingOutboundOrders]
ADMIN       → [getPlatformSummary, getMonthlyRevenue]
INSPECTOR   → [getMyAssignedInspections, getInspectionDetail]
```

> ⚠️ AI **không thể gọi tool ngoài danh sách role** của mình.
> GUEST hỏi "hợp đồng của tôi" → chỉ có `askLoginPrompt` → tự hướng dẫn đăng nhập.

---

## ⚠️ Điểm Sync Giữa 2 Dev (Ngày 1 — Bắt buộc)

- [ ] **Dev A định nghĩa `ChatTool` interface** → Dev B implement tools của mình dựa trên interface này
- [ ] **Dev A hoàn thành `ChatToolRegistry`** → Dev B đăng ký tools vào registry
- [ ] **Dev A hoàn thành `GeminiClient`** → Dev B dùng để test tools
- [ ] **Dev B expose internal methods** cần thiết cho tools:
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
- [ ] **`WebClientConfig.java`** — `@Configuration`:
  ```java
  @Bean
  public WebClient webClient() {
      return WebClient.builder()
          .baseUrl("https://generativelanguage.googleapis.com")
          .clientConnector(new ReactorClientHttpConnector(
              HttpClient.create()
                  .responseTimeout(Duration.ofSeconds(30))
                  .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
          ))
          .build();
  }
  ```
- [ ] Kiểm tra `pom.xml` đã có `spring-boot-starter-webflux` chưa, nếu chưa thêm vào

#### 1.2. ChatTool Interface ⚠️ (Dev B cần đợi cái này)
- [ ] **`ChatTool.java`** — interface chuẩn:
  ```java
  public interface ChatTool {
      String getName();           // "searchWarehouses"
      String getDescription();    // Mô tả ngắn cho Gemini biết khi nào dùng
      Map<String, Object> getParameterSchema(); // JSON Schema tham số
      String execute(Map<String, Object> params, UUID userId); // trả về JSON string
  }
  ```

#### 1.3. ChatToolRegistry ⚠️ (Dev B cần đợi cái này)
- [ ] **`ChatToolRegistry.java`** — `@Component`:
  - Field: `Map<UserRole, List<ChatTool>> toolsByRole`
  - Constructor inject tất cả `List<ChatTool>` → phân loại theo role
  - Methods:
    - `List<ChatTool> getToolsForRole(UserRole role)`
    - `ChatTool findByName(String name)`
  - Mapping:
    - `GUEST → [SearchWarehousesTool, GetWarehouseDetailTool, AskLoginPromptTool]`
    - `TENANT → GUEST + [GetMyContractsTool, GetContractDetailTool, GetMyStockTool, GetMyWalletTool]`
    - `OWNER → [GetMyWarehousesTool, GetWarehouseBookingsTool, GetRevenueSummaryTool, GetOccupancyTool]`
    - `STAFF → [GetAssignedStockTool, GetPendingInboundTool, GetPendingOutboundTool]`
    - `ADMIN → [GetPlatformSummaryTool, GetMonthlyRevenueTool]`
    - `INSPECTOR → [GetMyInspectionsTool, GetInspectionDetailTool]`

#### 1.4. GeminiClient (hỗ trợ Function Calling)
- [ ] **`GeminiClient.java`** — `@Component`:

  **Inner records:**
  ```java
  record GeminiResponse(String text, FunctionCall functionCall) {
      boolean isFunctionCall() { return functionCall != null; }
  }
  record FunctionCall(String name, Map<String, Object> args) {}
  record FunctionDeclaration(String name, String description, Map<String, Object> parameters) {}
  ```

  **Methods:**
  - `GeminiResponse chatWithTools(List<ChatMessage> history, String systemPrompt, String userMessage, List<ChatTool> tools)`
  - `GeminiResponse sendToolResult(List<Object> fullConversation, String toolName, String toolResult)`

  **Request body format (Gemini Function Calling):**
  ```json
  {
    "contents": [
      {"role": "user", "parts": [{"text": "..."}]},
      {"role": "model", "parts": [{"text": "..."}]}
    ],
    "systemInstruction": {"parts": [{"text": "system prompt"}]},
    "tools": [{
      "functionDeclarations": [
        {
          "name": "searchWarehouses",
          "description": "Tìm kiếm kho theo bộ lọc",
          "parameters": {
            "type": "OBJECT",
            "properties": {
              "city": {"type": "STRING", "description": "Thành phố"},
              "warehouseType": {"type": "STRING"},
              "minPrice": {"type": "NUMBER"},
              "maxPrice": {"type": "NUMBER"}
            }
          }
        }
      ]
    }],
    "generationConfig": {"maxOutputTokens": 1024, "temperature": 0.7}
  }
  ```
  **Error handling:** `429` → throw `GEMINI_API_QUOTA_EXCEEDED`, khác → throw `GEMINI_API_ERROR`

#### 1.5. PromptBuilder (6 system prompts theo role)
- [ ] **`PromptBuilder.java`** — `@Component`, `buildSystemPrompt(UserRole role)` → `String`:
  - **GUEST:** *"Trợ lý AI StockSpace. Chỉ tư vấn thông tin kho công khai. Nếu user hỏi thông tin cá nhân (hợp đồng, tồn kho, ví...), dùng tool `askLoginPrompt`."*
  - **TENANT:** *"Trợ lý cá nhân Tenant. Hỗ trợ xem hợp đồng, tồn kho kho đang thuê, số dư ví. Không truy cập dữ liệu Tenant khác."*
  - **OWNER:** *"Trợ lý quản lý kho Owner. Hỗ trợ theo dõi doanh thu, tình trạng cho thuê, booking đang chờ."*
  - **STAFF:** *"Trợ lý WMS. Hỗ trợ nhập/xuất hàng, kiểm tra tồn kho kho được phân công."*
  - **ADMIN:** *"Trợ lý Admin. Xem tổng quan toàn hệ thống, báo cáo doanh thu."*
  - **INSPECTOR:** *"Trợ lý Inspector. Xem lịch kiểm định, chi tiết yêu cầu được phân công."*

#### 1.6. ChatbotService (Agentic Loop — Core Logic)
- [ ] **`ChatbotService.java`** — `@Service`:

  **`processMessage(UUID userId, UserRole role, SendMessageRequest request)` → `ChatResponse`:**
  ```
  1. Load/tạo ChatSession (theo sessionId hoặc tạo mới, gắn userId)
  2. Lấy 10 tin nhắn gần nhất: findTop10BySessionIdOrderByCreatedAtDesc(sessionId)
  3. systemPrompt = PromptBuilder.buildSystemPrompt(role)
  4. tools = ChatToolRegistry.getToolsForRole(role)
  5. response = GeminiClient.chatWithTools(history, systemPrompt, message, tools)
  6. --- AGENTIC LOOP (tối đa 5 vòng) ---
     int iteration = 0;
     while (response.isFunctionCall() && iteration < 5) {
         tool = registry.findByName(response.functionCall().name())
         result = tool.execute(response.functionCall().args(), userId)
         response = geminiClient.sendToolResult(conversation, toolName, result)
         iteration++
     }
  7. Lưu ChatMessage USER (role="user") vào DB
  8. Lưu ChatMessage MODEL (role="model") vào DB
  9. Nếu session mới → title = message.substring(0, min(50, message.length()))
  10. Return ChatResponse(sessionId, sessionToken, botReply)
  ```

  **`processGuestMessage(String sessionToken, SendMessageRequest request)` → `ChatResponse`:**
  - Role cố định = `GUEST`, `userId = null`
  - Nếu `sessionToken` null → generate `UUID.randomUUID().toString()`
  - Load session bằng `findBySessionTokenAndIsDeletedFalse(token)`
  - Chạy agentic loop tương tự, tools của GUEST không cần `userId`

  Các method quản lý session:
  - `getMySessions(UUID userId, Pageable)` → `Page<ChatSessionResponse>`
  - `getSessionMessages(UUID userId, UUID sessionId)` → `List<ChatMessageResponse>`
  - `deleteSession(UUID userId, UUID sessionId)` — soft delete
  - `getGuestHistory(String sessionToken)` → `List<ChatMessageResponse>`

#### 1.7. Tools — GUEST & TENANT (Dev A implement)
- [ ] **`SearchWarehousesTool`** — query `WarehouseRepository` AVAILABLE, filter city/type/price/area, top 5, return JSON
- [ ] **`GetWarehouseDetailTool`** — query `WarehouseRepository.findById`, return detail JSON
- [ ] **`AskLoginPromptTool`** — không query DB, return: `{"action":"PROMPT_LOGIN","message":"Vui lòng đăng nhập để xem thông tin này"}`
- [ ] **`GetMyContractsTool`** — gọi `ContractService.getMyContracts(userId)`, serialize JSON
- [ ] **`GetContractDetailTool`** — params: `contractId`, gọi `ContractService.getContractDetail(contractId, userId)`
- [ ] **`GetMyStockTool`** — params: `warehouseId`, gọi `StockBatchService.getSummaryByWarehouse(warehouseId)`
- [ ] **`GetMyWalletTool`** — gọi `WalletService.getBalance(userId)`, return JSON

#### 1.8. DTOs bổ sung
- [ ] **`ChatSessionResponse.java`** — `id`, `title`, `createdAt`, `updatedAt`

#### 1.9. Controllers
**`UserChatController.java`** — `@RequestMapping("/api/chat")`, `@PreAuthorize("isAuthenticated()")`

| Method | Path | Mô tả |
|--------|------|--------|
| `POST` | `/api/chat/send` | Gửi tin nhắn (auto-detect role từ JWT) |
| `GET` | `/api/chat/sessions` | Danh sách phiên hội thoại (phân trang) |
| `GET` | `/api/chat/sessions/{id}/messages` | Lịch sử tin nhắn |
| `DELETE` | `/api/chat/sessions/{id}` | Xóa mềm phiên |

**`GuestChatController.java`** — `@RequestMapping("/api/chat/guest")`, Public

| Method | Path | Mô tả |
|--------|------|--------|
| `POST` | `/api/chat/guest/send` | Gửi tin nhắn không cần đăng nhập |
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

- [ ] `CHAT_TOOL_EXECUTION_ERROR("Không thể lấy dữ liệu yêu cầu, vui lòng thử lại", HttpStatus.BAD_GATEWAY)`

---

## 📊 Tóm Tắt Phân Công

| # | Nhiệm vụ | Dev | Ưu tiên | Trạng thái |
|---|----------|-----|---------|-----------|
| 1 | `WebClientConfig` + kiểm tra pom.xml | **Dev A** | 🔴 Ngày 1 | ⬜ |
| 2 | `ChatTool` interface | **Dev A** | 🔴 Ngày 1 | ⬜ |
| 3 | `ChatToolRegistry` | **Dev A** | 🔴 Ngày 1 | ⬜ |
| 4 | `GeminiClient` (function calling) | **Dev A** | 🔴 Ngày 1 | ⬜ |
| 5 | `PromptBuilder` (6 role prompts) | **Dev A** | 🟡 | ⬜ |
| 6 | `ChatbotService` (agentic loop) | **Dev A** | 🟡 | ⬜ |
| 7 | Tools: GUEST (3 tools) | **Dev A** | 🟡 | ⬜ |
| 8 | Tools: TENANT (4 tools) | **Dev A** | 🟡 | ⬜ |
| 9 | `ChatSessionResponse` DTO | **Dev A** | 🟢 | ⬜ |
| 10 | User/Guest Chat Controllers | **Dev A** | 🟢 | ⬜ |
| 11 | Expose internal methods (Contract/Wallet/Stock/Booking/Inspection) | **Dev B** | 🔴 Ngày 1 | ⬜ |
| 12 | Tools: OWNER (4 tools) | **Dev B** | 🟡 | ⬜ |
| 13 | Tools: STAFF (3 tools) | **Dev B** | 🟡 | ⬜ |
| 14 | Tools: ADMIN (2 tools) | **Dev B** | 🟡 | ⬜ |
| 15 | Tools: INSPECTOR (2 tools) | **Dev B** | 🟡 | ⬜ |
| 16 | Owner Stats Service + Controller + DTOs | **Dev B** | 🟢 | ⬜ |
| 17 | Admin Stats Service + Controller + DTOs | **Dev B** | 🟢 | ⬜ |
