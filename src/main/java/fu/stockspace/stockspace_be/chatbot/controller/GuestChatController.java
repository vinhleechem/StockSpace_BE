package fu.stockspace.stockspace_be.chatbot.controller;

import fu.stockspace.stockspace_be.chatbot.dto.*;
import fu.stockspace.stockspace_be.chatbot.service.ChatbotService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Controller xử lý chat cho GUEST (không cần đăng nhập).
 * SecurityConfig đã permit /api/chat/guest/** cho tất cả.
 *
 * Endpoints:
 *   POST /api/chat/guest/send    — Gửi tin nhắn không cần đăng nhập
 *   GET  /api/chat/guest/history — Lịch sử chat bằng bearer token trong header
 */
@Tag(name = "Chatbot - Guest", description = "Chat API công khai cho khách vãng lai")
@RestController
@RequestMapping("/api/chat/guest")
@RequiredArgsConstructor
public class GuestChatController {

    public static final String SESSION_TOKEN_HEADER = "X-Chat-Session-Token";

    private final ChatbotService chatbotService;

    @Operation(
            summary = "Gửi tin nhắn (Guest)",
            description = "Không cần đăng nhập. Server cấp sessionToken trong response; các lần sau gửi token bằng header X-Chat-Session-Token. Query parameter chỉ giữ để tương thích client cũ."
    )
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @Parameter(description = "Guest session bearer token do server cấp")
            @RequestHeader(name = SESSION_TOKEN_HEADER, required = false) String headerToken,
            @Parameter(description = "Deprecated: dùng header X-Chat-Session-Token", deprecated = true)
            @RequestParam(name = "sessionToken", required = false) String legacyQueryToken,
            @Valid @RequestBody SendMessageRequest request) {

        ChatResponse response = chatbotService.processGuestMessage(
                resolveSessionToken(headerToken, legacyQueryToken),
                request
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Stream phản hồi chatbot (Guest)",
            description = "SSE v1 qua POST. Event đầu tiên là session và chứa guest sessionToken; client tiếp tục gửi token bằng header X-Chat-Session-Token."
    )
    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> streamMessage(
            @Parameter(description = "Guest session bearer token do server cấp")
            @RequestHeader(name = SESSION_TOKEN_HEADER, required = false) String headerToken,
            @Parameter(description = "Deprecated: dùng header X-Chat-Session-Token", deprecated = true)
            @RequestParam(name = "sessionToken", required = false) String legacyQueryToken,
            @Valid @RequestBody SendMessageRequest request) {

        SseEmitter emitter = chatbotService.streamGuestMessage(
                resolveSessionToken(headerToken, legacyQueryToken),
                request
        );
        return streamResponse(emitter);
    }

    @Operation(
            summary = "Lịch sử chat (Guest)",
            description = "Trả về tối đa 200 tin nhắn gần nhất. Ưu tiên header X-Chat-Session-Token."
    )
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getHistory(
            @Parameter(description = "Guest session bearer token do server cấp")
            @RequestHeader(name = SESSION_TOKEN_HEADER, required = false) String headerToken,
            @Parameter(description = "Deprecated: dùng header X-Chat-Session-Token", deprecated = true)
            @RequestParam(name = "sessionToken", required = false) String legacyQueryToken) {

        String sessionToken = resolveSessionToken(headerToken, legacyQueryToken);
        if (sessionToken == null) {
            throw new BadRequestException("Thiếu guest session token");
        }
        List<ChatMessageResponse> messages =
                chatbotService.getGuestHistory(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    private String resolveSessionToken(String headerToken, String legacyQueryToken) {
        String header = normalize(headerToken);
        String query = normalize(legacyQueryToken);
        if (header != null && query != null && !header.equals(query)) {
            throw new BadRequestException("Guest session token không nhất quán");
        }
        return header != null ? header : query;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, no-transform")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
