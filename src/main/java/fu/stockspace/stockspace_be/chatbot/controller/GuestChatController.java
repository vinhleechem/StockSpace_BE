package fu.stockspace.stockspace_be.chatbot.controller;

import fu.stockspace.stockspace_be.chatbot.dto.*;
import fu.stockspace.stockspace_be.chatbot.service.ChatbotService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller xử lý chat cho GUEST (không cần đăng nhập).
 * SecurityConfig đã permit /api/chat/guest/** cho tất cả.
 *
 * Endpoints:
 *   POST /api/chat/guest/send    — Gửi tin nhắn không cần đăng nhập
 *   GET  /api/chat/guest/history — Lịch sử chat theo ?sessionToken=...
 */
@Tag(name = "Chatbot - Guest", description = "Chat API công khai cho khách vãng lai")
@RestController
@RequestMapping("/api/chat/guest")
@RequiredArgsConstructor
public class GuestChatController {

    private final ChatbotService chatbotService;

    @Operation(
            summary = "Gửi tin nhắn (Guest)",
            description = "Không cần đăng nhập. sessionToken được trả về trong response để dùng cho lần tiếp theo."
    )
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @RequestParam(required = false) String sessionToken,
            @Valid @RequestBody SendMessageRequest request) {

        ChatResponse response = chatbotService.processGuestMessage(sessionToken, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Lịch sử chat (Guest)",
            description = "Trả về lịch sử tin nhắn của guest session theo sessionToken"
    )
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getHistory(
            @RequestParam String sessionToken) {

        List<ChatMessageResponse> messages = chatbotService.getGuestHistory(sessionToken);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }
}
