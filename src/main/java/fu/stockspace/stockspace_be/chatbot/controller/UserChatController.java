package fu.stockspace.stockspace_be.chatbot.controller;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.chatbot.dto.*;
import fu.stockspace.stockspace_be.chatbot.service.ChatbotService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;










@Tag(name = "Chatbot - User", description = "Chat API cho user đã đăng nhập")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('CHAT_USE')")
public class UserChatController {

    private final ChatbotService chatbotService;

    @Operation(summary = "Gửi tin nhắn tới chatbot", description = "Auto-detect role từ JWT để cấp đúng tools cho AI. Frontend có thể gửi activeWarehouseId của kho đang mở; giá trị này chỉ là context và luôn được backend kiểm tra quyền trước khi sử dụng.")
    @PostMapping("/send")
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(
            @Valid @RequestBody SendMessageRequest request) {

        UUID userId = SecurityUtil.getCurrentUserId();
        Role role = SecurityUtil.getCurrentRole();
        String roleName = role != null ? role.getName() : "GUEST";

        ChatResponse response = chatbotService.processMessage(userId, roleName, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Stream phản hồi chatbot",
            description = "SSE v1 qua POST; danh tính và role được lấy từ JWT trước khi chuyển xử lý sang worker."
    )
    @PostMapping(
            value = "/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public ResponseEntity<SseEmitter> streamMessage(
            @Valid @RequestBody SendMessageRequest request) {

        UUID userId = SecurityUtil.getCurrentUserId();
        Role role = SecurityUtil.getCurrentRole();
        String roleName = role != null ? role.getName() : "GUEST";

        SseEmitter emitter =
                chatbotService.streamMessage(userId, roleName, request);
        return streamResponse(emitter);
    }

    @Operation(summary = "Danh sách phiên hội thoại", description = "Trả về danh sách sessions của user, sắp xếp mới nhất trước")
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<Page<ChatSessionResponse>>> getSessions(
            @PageableDefault(size = 20) Pageable pageable) {

        UUID userId = SecurityUtil.getCurrentUserId();
        Pageable boundedPageable = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.max(1, Math.min(50, pageable.getPageSize()))
        );
        Page<ChatSessionResponse> sessions =
                chatbotService.getMySessions(userId, boundedPageable);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @Operation(summary = "Lịch sử tin nhắn", description = "Lấy tối đa 200 tin nhắn gần nhất trong một phiên hội thoại")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @PathVariable UUID sessionId) {

        UUID userId = SecurityUtil.getCurrentUserId();
        List<ChatMessageResponse> messages = chatbotService.getSessionMessages(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @Operation(summary = "Xóa phiên hội thoại", description = "Xóa mềm một phiên hội thoại — không thể khôi phục")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<String>> deleteSession(
            @PathVariable UUID sessionId) {

        UUID userId = SecurityUtil.getCurrentUserId();
        chatbotService.deleteSession(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa phiên hội thoại"));
    }

    private ResponseEntity<SseEmitter> streamResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, no-transform")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
