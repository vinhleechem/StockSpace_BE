# StockSpace Chatbot

Chatbot dùng OpenRouter Chat Completions với tool calling theo vai trò, hội thoại
được lưu trong PostgreSQL và RAG hybrid cho cơ sở tri thức chính sách.

## Cấu hình bắt buộc

Production cần tối thiểu:

```dotenv
JWT_SECRET=<base64-or-base64url-secret-at-least-256-bits>
OPENROUTER_API_KEY=<openrouter-key>
OPENROUTER_MODEL=<tool-calling-model-with-zdr-provider>
OPENROUTER_DATA_COLLECTION=deny
OPENROUTER_ZDR=true
```

Profile `prod` sẽ dừng khởi động nếu thiếu JWT/OpenRouter hoặc tắt thiết lập
privacy. Không dùng model `:free` cho dữ liệu hợp đồng, ví hoặc tồn kho riêng.
Tham khảo [OpenRouter tool calling](https://openrouter.ai/docs/guides/features/tool-calling),
[ZDR](https://openrouter.ai/docs/guides/features/zdr) và
[embeddings](https://openrouter.ai/docs/api/reference/embeddings).

Các biến có giá trị mặc định vận hành nằm trong
`src/main/resources/application.properties` và `.env.example`, gồm timeout,
bulkhead, agent-loop budget, rate limit, guest TTL và RAG indexer.

Khi nâng cấp database đã có dữ liệu, chạy migration idempotent
`ops/migrations/20260728_chatbot_production.sql`. `deploy.sh deploy` tự chạy
migration này trước khi khởi động image ứng dụng mới.

## API

User đã đăng nhập:

```http
POST /api/chat/send
Authorization: Bearer <access-token>
Content-Type: application/json

{"sessionId": null, "message": "Tôi còn bao nhiêu hàng trong kho?"}
```

Guest, lượt đầu không gửi token:

```http
POST /api/chat/guest/send
Content-Type: application/json

{"sessionId": null, "message": "Tìm kho ở Quận 7"}
```

Server trả `sessionToken`. Từ lượt sau và khi đọc history, gửi token đó bằng
header; query parameter chỉ còn để tương thích client cũ:

```http
X-Chat-Session-Token: <server-issued-token>
```

Token guest có TTL rolling, được băm SHA-256 trong database và token lạ do
client tự chọn sẽ bị từ chối. Session/message guest hết hạn được purge theo
batch sau thời gian grace cấu hình (mặc định 7 ngày).

### SSE streaming

Hai endpoint `/send` vẫn trả JSON hoàn chỉnh để tương thích client cũ. Client
mới nên dùng:

```http
POST /api/chat/stream
Authorization: Bearer <access-token>
Accept: text/event-stream
Content-Type: application/json

POST /api/chat/guest/stream
Accept: text/event-stream
Content-Type: application/json
X-Chat-Session-Token: <server-issued-token>  # bỏ ở lượt guest đầu
```

Mỗi frame tuân theo chuẩn SSE và có một trong các event sau:

| Event | Payload |
| --- | --- |
| `session` | `{version, requestId, sessionId, sessionToken?, sessionCreated}` |
| `status` | `{requestId, message}` |
| `delta` | `{requestId, sequence, content}` |
| `ping` | `{requestId, timestamp}` |
| `complete` | `{requestId, sessionId, timestamp}` |
| `error` | `{requestId, code, message, retryable}` |

`session` mở đầu stream. `delta.sequence` tăng dần và `content` chỉ chứa phần
text mới. `ping` giữ kết nối qua proxy khi agent đang gọi tool. Stream kết thúc
bằng đúng một `complete` hoặc `error`; `complete` không lặp lại toàn bộ câu trả
lời. Với guest mới, lưu `sessionToken` từ event `session` và gửi lại bằng header
ở lượt sau. Lỗi xảy ra trước khi stream được mở vẫn có thể là HTTP/JSON; sau khi
đã mở stream, lỗi được gửi bằng event `error`.

Endpoint là POST và cần custom header, vì vậy frontend dùng Fetch streaming,
không dùng `EventSource` native:

```javascript
const response = await fetch(`${apiUrl}/api/chat/guest/stream`, {
  method: "POST",
  headers: {
    Accept: "text/event-stream",
    "Content-Type": "application/json",
    ...(sessionToken
      ? {"X-Chat-Session-Token": sessionToken}
      : {}),
  },
  body: JSON.stringify({sessionId, message}),
  signal: abortController.signal,
});

if (!response.ok || !response.body) {
  throw new Error(`Chat request failed: ${response.status}`);
}

const reader = response.body
  .pipeThrough(new TextDecoderStream())
  .getReader();
let buffer = "";

while (true) {
  const {value, done} = await reader.read();
  if (done) break;
  buffer += value;
  buffer = buffer.replaceAll("\r\n", "\n");

  let boundary;
  while ((boundary = buffer.indexOf("\n\n")) >= 0) {
    const frame = buffer.slice(0, boundary);
    buffer = buffer.slice(boundary + 2);
    const event = frame.match(/^event:\s*(.+)$/m)?.[1];
    const data = frame
      .split("\n")
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart())
      .join("\n");
    if (event && data) handleChatEvent(event, JSON.parse(data));
  }
}
```

Kiểm tra trên terminal bằng `-N` để curl không tự buffer output:

```bash
curl -N --http1.1 --max-time 120 \
  -X POST http://localhost:8080/api/chat/guest/stream \
  -H 'Accept: text/event-stream' \
  -H 'Content-Type: application/json; charset=utf-8' \
  --data '{"sessionId":null,"message":"Chính sách đặt cọc là gì?"}'
```

Phải smoke-test thêm qua Nginx production, không chỉ port `8080`, để xác nhận
các event `delta` tới trước `complete` thay vì bị proxy dồn thành một response.

## Tool theo vai trò

| Vai trò | Tool |
| --- | --- |
| Guest | `searchWarehouses`, `getWarehouseDetail`, `searchSystemPolicy`, `askLoginPrompt` |
| Tenant | Ba tool public/RAG + `getMyContracts`, `getContractDetail`, `getMyStock`, `getMyWallet` |
| Owner, Staff, Admin, Inspector | Ba tool public/RAG; tool riêng chờ module tương ứng |

Agent chỉ thực thi tool trong allowlist của chính request. Tool tenant kiểm tra
user/contract/subscription ở service và chỉ trả DTO tối thiểu, không đưa PII hay
chứng từ riêng sang model.

## RAG

`DataInitializer` chỉ upsert tài liệu có `sourceId` ổn định và không gọi mạng.
Tra cứu lexical hoạt động ngay cả khi embedding provider lỗi. Khi embedding hợp
lệ, retrieval dùng hybrid lexical/cosine và chỉ dùng vector khớp model,
dimension và content hash.

Indexer chạy theo batch, gọi embedding ngoài transaction rồi kiểm tra lại hash
trước khi lưu. Dev mặc định tắt; profile production mặc định bật:

```dotenv
CHATBOT_RAG_INDEXER_ENABLED=true
CHATBOT_RAG_INDEXER_BATCH_SIZE=32
CHATBOT_RAG_INDEXER_INTERVAL_MS=3600000
CHATBOT_RAG_INDEXER_INITIAL_DELAY_MS=60000
CHATBOT_PGVECTOR_ENABLED=true
CHATBOT_PGVECTOR_SCHEMA_INITIALIZER_ENABLED=true
CHATBOT_PGVECTOR_EF_SEARCH=100
```

Semantic retrieval dùng pgvector thật: cột `embedding_vector vector(1536)` và
partial HNSW index `vector_cosine_ops` chỉ chứa tài liệu active, chưa bị xóa và
có vector. PostgreSQL thực hiện nearest-neighbor cosine; giới hạn candidate vẫn
được giữ để kiểm soát latency và chi phí hybrid ranking.
Mỗi truy vấn bật `hnsw.iterative_scan=strict_order` trong transaction và dùng
`CHATBOT_PGVECTOR_EF_SEARCH` làm search breadth, tránh hụt kết quả khi lọc thêm
category/model/dimension sau ANN scan.

`schema.sql` cài extension `vector` trước khi JPA khởi tạo. Migration production
thêm cột, ràng buộc cấm zero vector và HNSW index theo kiểu additive/idempotent.
Runner hậu JPA có advisory lock để hoàn tất cùng DDL trên database mới, nơi
migration chạy trước Hibernate nên chưa thấy bảng `system_knowledge`.
Docker pin `pgvector/pgvector:0.8.5-pg16`; tài khoản chạy migration phải có
quyền tạo/nâng cấp extension (Compose dùng chính owner PostgreSQL).

Trong một release chuyển đổi, indexer dual-write cả `embedding_vector` và cột
legacy `embedding_str` dạng `TEXT`. Giữ `CHATBOT_PGVECTOR_ENABLED=false` như
rollback switch tức thời về retrieval TEXT/JVM; chỉ xóa cột TEXT sau khi release
rollback đã hết vòng đời và backfill vector đã hoàn tất.
Trong lúc backfill một phần, read path merge cả điểm pgvector và điểm TEXT của
tài liệu chưa có native match; vì vậy batch native đầu tiên không làm mất recall
của phần dữ liệu legacy còn lại. Lỗi 400/413/422 do một input embedding được
chia đôi batch để cô lập tài liệu lỗi, không chặn vô hạn các tài liệu phía sau.

## Vận hành

- Nginx và filter ứng dụng cùng giới hạn endpoint guest AI.
- User đã đăng nhập có quota theo user và giới hạn request chạy đồng thời.
- Provider payload, tool argument/result và guest token không được ghi log.
- Production refresh cookie dùng `Secure`; phải đặt TLS ở Nginx hoặc load
  balancer/CDN tin cậy phía trước.
- `deploy.sh` từ chối deploy cho tới khi xác nhận `PUBLIC_HTTPS_READY=true`;
  Adminer không còn được publish qua reverse proxy production.
- Demo account mặc định tắt và bị khóa cứng trong profile production.
- History API trả tối đa 200 message gần nhất; model nhận tối đa 10 message gần
  nhất làm context.
- Project hiện vẫn dùng Hibernate `ddl-auto=update` cho các module legacy chưa
  có migration đầy đủ. Khi toàn schema đã được quản lý bằng migration, chạy
  runtime role ít quyền với `ddl-auto=validate`, `SPRING_SQL_INIT_MODE=never`
  và tắt `CHATBOT_PGVECTOR_SCHEMA_INITIALIZER_ENABLED` sau bootstrap.

Chạy kiểm tra chatbot:

```bash
mvn test
```
