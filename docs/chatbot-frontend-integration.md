# StockSpace Chatbot — Hướng dẫn tích hợp Frontend

> Tài liệu này mô tả cách Frontend cần gọi API chatbot để chatbot hoạt động đúng ngữ cảnh kho.

---

## 1. API Endpoint

| Mode | Endpoint | Mô tả |
|---|---|---|
| Đồng bộ | `POST /api/chat/send` | Trả response JSON một lần |
| Streaming (SSE) | `POST /api/chat/stream` | Trả dữ liệu từng chunk qua SSE |

**Request body (chung cho cả hai):**

```json
{
  "sessionId": "uuid-phiên-hiện-tại-hoặc-null-nếu-tạo-mới",
  "message": "Nội dung tin nhắn của user",
  "activeWarehouseId": "uuid-kho-đang-mở-hoặc-null"
}
```

---

## 2. Field `activeWarehouseId` — Quan trọng nhất

### Ý nghĩa
- Cho chatbot biết user đang đứng ở kho nào trên giao diện.
- Backend xác minh quyền truy cập trước khi dùng — Frontend **không cần tự kiểm tra**.
- Chatbot dùng giá trị này làm **kho mặc định** khi user hỏi về tồn kho, phiếu, kiểm kê, chuyển hàng, v.v.

### Quy tắc truyền

```typescript
// Gửi warehouseId của kho đang được chọn trên trang hiện tại
// Gửi null nếu trang không có ngữ cảnh kho cụ thể

const body = {
  sessionId: currentSessionId ?? null,
  message: userMessage,
  activeWarehouseId: currentWarehouseId ?? null,
}
```

### Lấy `currentWarehouseId` từ đâu

```typescript
// Ưu tiên theo thứ tự:
const currentWarehouseId =
  router.query.warehouseId          // 1. URL/query param
  ?? pageWarehouseStore.selectedId  // 2. State/store của trang
  ?? null                           // 3. Không có → truyền null
```

---

## 3. Bảng quy tắc theo từng trang

| Trang | `activeWarehouseId` cần truyền |
|---|---|
| Inventory (`/tenant/inventory`) | `warehouseId` của kho đang chọn trong dropdown góc phải |
| Inbound (`/tenant/inbound`) | `warehouseId` đang chọn |
| Outbound (`/tenant/outbound`) | `warehouseId` đang chọn |
| Audits (`/tenant/audits`) | `warehouseId` đang chọn |
| Transfers (`/tenant/transfers`) | `warehouseId` đang chọn |
| Warehouse Layout (`/tenant/warehouse-layout`) | `warehouseId` đang chọn |
| Dashboard (`/tenant/dashboard`) | `null` |
| Wallet (`/tenant/wallet`) | `null` |
| Subscription (`/tenant/subscription`) | `null` |
| My Contracts (`/tenant/my-contracts`) | `null` |
| Staff (`/tenant/staff`) | `null` |
| SKU Mgt / Category Mgt | `null` |

---

## 4. Hành vi chatbot theo từng trường hợp

### Truyền đúng `activeWarehouseId`
```
User đứng Inventory → Kho Vũng Tàu
Frontend gửi: activeWarehouseId = "c31808fc-..."

→ Chatbot tự biết đang nói về Kho Vũng Tàu
→ Trả kết quả ngay, không hỏi lại
```

### Truyền `null` và user hỏi kho cụ thể
```
User đứng Dashboard
Frontend gửi: activeWarehouseId = null

User: "Tồn kho Kho Vũng Tàu thế nào?"
→ Chatbot tự gọi getMyActiveWarehouses để tìm ID kho
→ Trả kết quả đúng kho, không yêu cầu user đổi trang
```

### Truyền `null` và user hỏi chung chung
```
User: "Tồn kho của tôi thế nào?" (không nói kho nào)
→ Chatbot hỏi: "Bạn muốn xem kho nào: Kho Vũng Tàu hay Kho Bà Rịa?"
→ User trả lời tên kho → chatbot tự resolve
```

### User đứng Kho A nhưng hỏi Kho B
```
Frontend gửi: activeWarehouseId = <Kho Bà Rịa>
User: "Tồn kho Kho Vũng Tàu thế nào?"

→ Chatbot tự gọi getMyActiveWarehouses
→ Tìm đúng ID Kho Vũng Tàu
→ Trả kết quả Kho Vũng Tàu, bỏ qua context Kho Bà Rịa
```

---

## 5. Cập nhật `activeWarehouseId` khi user đổi kho

Khi user **đổi kho trong dropdown** giữa chừng (không reload trang), Frontend cần cập nhật `activeWarehouseId` cho các message tiếp theo:

```typescript
// Ví dụ với React state
const [activeWarehouseId, setActiveWarehouseId] = useState<string | null>(null)

// Khi user đổi kho
function onWarehouseChange(warehouseId: string) {
  setActiveWarehouseId(warehouseId)
  // Không cần reset session chat — chatbot tự hiểu context mới
}

// Khi gửi message
async function sendMessage(text: string) {
  await fetch('/api/chat/stream', {
    method: 'POST',
    body: JSON.stringify({
      sessionId: currentSessionId,
      message: text,
      activeWarehouseId: activeWarehouseId, // luôn dùng giá trị mới nhất
    })
  })
}
```

---

## 6. Không cần làm

| Việc | Lý do không cần |
|---|---|
| Dropdown chọn kho trong chatbot UI | Backend tự resolve tên → ID qua AI |
| Reset session khi đổi trang hoặc đổi kho | Session vẫn dùng được, context cập nhật qua field `activeWarehouseId` |
| Validate quyền truy cập kho trước khi gửi | Backend đã xác minh trong `ActiveWarehouseContextResolver` |
| Gọi API nào khác để resolve warehouseId | Không cần, chỉ cần truyền ID đang có trên trang |

---

## 7. Ví dụ hoàn chỉnh (TypeScript / Fetch)

```typescript
interface ChatRequest {
  sessionId?: string | null
  message: string
  activeWarehouseId?: string | null
}

async function streamChat(request: ChatRequest): Promise<ReadableStream> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getAccessToken()}`,
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) throw new Error('Chat request failed')
  return response.body!
}

// Sử dụng:
const warehouseId = useWarehouseStore(state => state.selectedWarehouseId) // null nếu không có

await streamChat({
  sessionId: sessionId,
  message: userInput,
  activeWarehouseId: warehouseId,
})
```

---

## 8. Lưu ý bảo mật

- `activeWarehouseId` được **xác minh phía backend** — nếu tenant không có quyền truy cập kho đó, backend sẽ bỏ qua và trả về context không có kho.
- Không cần lo việc user tự sửa `activeWarehouseId` để xem kho người khác — backend chặn điều này.
- UUID kho **không được hiển thị** trong response chatbot theo chính sách hệ thống.
