package fu.stockspace.stockspace_be.chatbot.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.chatbot.dto.SendMessageRequest;
import fu.stockspace.stockspace_be.chatbot.service.ChatbotService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStreamControllerTest {

    @Test
    void guestStreamUsesHeaderTokenAndReturnsAntiBufferingHeaders() {
        ChatbotService chatbotService = mock(ChatbotService.class);
        GuestChatController controller = new GuestChatController(chatbotService);
        SseEmitter emitter = new SseEmitter();
        SendMessageRequest request = new SendMessageRequest(null, "Xin chào");
        String token = UUID.randomUUID().toString();
        when(chatbotService.streamGuestMessage(token, request)).thenReturn(emitter);

        ResponseEntity<SseEmitter> response =
                controller.streamMessage(token, request);

        assertSame(emitter, response.getBody());
        assertEquals(MediaType.TEXT_EVENT_STREAM, response.getHeaders().getContentType());
        assertEquals("no", response.getHeaders().getFirst("X-Accel-Buffering"));
        assertEquals(
                "no-cache, no-store, no-transform",
                response.getHeaders().getCacheControl()
        );
        verify(chatbotService).streamGuestMessage(token, request);
    }

    @Test
    void authenticatedStreamCapturesIdentityBeforeCallingAsyncService() {
        ChatbotService chatbotService = mock(ChatbotService.class);
        UserChatController controller = new UserChatController(chatbotService);
        SseEmitter emitter = new SseEmitter();
        SendMessageRequest request = new SendMessageRequest(null, "Kiểm tra ví");
        UUID userId = UUID.randomUUID();
        when(chatbotService.streamTenantMessage(userId, request))
                .thenReturn(emitter);

        try (MockedStatic<SecurityUtil> security = mockStatic(SecurityUtil.class)) {
            security.when(SecurityUtil::getCurrentUserId).thenReturn(userId);
            ResponseEntity<SseEmitter> response =
                    controller.streamMessage(request);

            assertSame(emitter, response.getBody());
            assertEquals(
                    MediaType.TEXT_EVENT_STREAM,
                    response.getHeaders().getContentType()
            );
        }

        verify(chatbotService).streamTenantMessage(userId, request);
    }
}
