package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient;
import fu.stockspace.stockspace_be.chatbot.config.AuthenticatedChatRateLimiter;
import fu.stockspace.stockspace_be.chatbot.config.ChatStreamRuntime;
import fu.stockspace.stockspace_be.chatbot.dto.ChatResponse;
import fu.stockspace.stockspace_be.chatbot.dto.SendMessageRequest;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatToolRegistry;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock
    private ChatConversationStore conversationStore;

    @Mock
    private ChatToolRegistry toolRegistry;

    @Mock
    private PromptBuilder promptBuilder;

    @Mock
    private ActiveWarehouseContextResolver activeWarehouseContextResolver;

    private OpenRouterClient openRouterClient;
    private ChatStreamRuntime chatStreamRuntime;
    private ChatbotService service;

    @BeforeEach
    void setUp() {
        openRouterClient = spy(new OpenRouterClient(
                mock(org.springframework.web.reactive.function.client.WebClient.class),
                new com.fasterxml.jackson.databind.ObjectMapper()
        ));
        chatStreamRuntime = mock(ChatStreamRuntime.class);
        service = new ChatbotService(
                conversationStore,
                openRouterClient,
                toolRegistry,
                promptBuilder,
                activeWarehouseContextResolver,
                new AuthenticatedChatRateLimiter(),
                chatStreamRuntime
        );
        ReflectionTestUtils.setField(service, "maxAgentIterations", 4);
        ReflectionTestUtils.setField(service, "requestDeadline", Duration.ofSeconds(10));
        ReflectionTestUtils.setField(service, "maxToolResultChars", 16_000);
        ReflectionTestUtils.setField(service, "maxAssistantResponseChars", 16_000);
    }


    private void configureStreamRuntime() {
        when(chatStreamRuntime.effectiveTimeout()).thenReturn(Duration.ofSeconds(90));
        when(chatStreamRuntime.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            FutureTask<Void> future = new FutureTask<>(task, null);
            future.run();
            return future;
        });
        when(chatStreamRuntime.scheduleHeartbeat(any(Runnable.class)))
                .thenReturn(mock(ScheduledFuture.class));
    }

    @Test
    void rejectsModelRequestedToolOutsideAllowlistAndKeepsFullTranscript() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatTool allowedTool = namedTool("searchWarehouses");
        List<ChatTool> allowedTools = List.of(allowedTool);
        OpenRouterClient.FunctionCall forbiddenCall =
                new OpenRouterClient.FunctionCall(
                        "call_forbidden",
                        "deleteWarehouse",
                        Map.of("warehouseId", UUID.randomUUID().toString())
                );

        when(conversationStore.prepareUserSession(userId, null))
                .thenReturn(new PreparedChatSession(sessionId, null, List.of()));
        when(toolRegistry.getToolsForRole("ROLE_TENANT")).thenReturn(allowedTools);
        ChatRequestContext resolvedContext = new ChatRequestContext(userId, null);
        when(activeWarehouseContextResolver.resolve(userId, null))
                .thenReturn(resolvedContext);
        when(promptBuilder.buildSystemPrompt(
                eq("ROLE_TENANT"), eq(allowedTools), eq(resolvedContext)))
                .thenReturn("system prompt");
        when(conversationStore.appendUserTurn(
                eq(userId), eq(sessionId), eq("xóa kho"), eq("Tôi không thể làm việc đó.")))
                .thenReturn(LocalDateTime.of(2026, 7, 28, 12, 0));
        doReturn(
                new OpenRouterClient.AiResponse(null, forbiddenCall),
                new OpenRouterClient.AiResponse("Tôi không thể làm việc đó.", null)
        ).when(openRouterClient).complete(
                anyList(),
                eq(allowedTools),
                org.mockito.ArgumentMatchers.any(Duration.class)
        );

        ChatResponse result = service.processTenantMessage(
                userId,
                new SendMessageRequest(null, "xóa kho")
        );

        assertEquals("Tôi không thể làm việc đó.", result.botReply());
        verify(allowedTool, never()).executeWithContext(
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.any(ChatRequestContext.class)
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> conversations =
                ArgumentCaptor.forClass(List.class);
        verify(openRouterClient, org.mockito.Mockito.times(2))
                .complete(
                        conversations.capture(),
                        eq(allowedTools),
                        org.mockito.ArgumentMatchers.any(Duration.class)
                );
        List<Map<String, Object>> finalTranscript =
                conversations.getAllValues().get(1);
        assertEquals(List.of("system", "user", "assistant", "tool"),
                finalTranscript.stream().map(message -> message.get("role")).toList());
        assertTrue(finalTranscript.get(2).containsKey("tool_calls"));
        assertEquals("call_forbidden", finalTranscript.get(3).get("tool_call_id"));
        assertTrue(String.valueOf(finalTranscript.get(3).get("content"))
                .contains("không được phép"));
        verify(conversationStore).appendUserTurn(
                userId,
                sessionId,
                "xóa kho",
                "Tôi không thể làm việc đó."
        );
    }

    @Test
    void passesActiveWarehouseContextToAllowedToolWithoutGivingItToModelArgs() {
        UUID userId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatTool allowedTool = namedTool("getMyStock");
        List<ChatTool> allowedTools = List.of(allowedTool);
        OpenRouterClient.FunctionCall stockCall = new OpenRouterClient.FunctionCall(
                "call_stock", "getMyStock", Map.of());

        when(conversationStore.prepareUserSession(userId, null))
                .thenReturn(new PreparedChatSession(sessionId, null, List.of()));
        when(toolRegistry.getToolsForRole("ROLE_TENANT")).thenReturn(allowedTools);
        ChatRequestContext resolvedContext = new ChatRequestContext(
                userId, warehouseId, "Kho A");
        when(activeWarehouseContextResolver.resolve(userId, warehouseId))
                .thenReturn(resolvedContext);
        when(promptBuilder.buildSystemPrompt(
                eq("ROLE_TENANT"), eq(allowedTools), eq(resolvedContext)))
                .thenReturn("system prompt");
        when(conversationStore.appendUserTurn(
                eq(userId), eq(sessionId), eq("Xem tồn kho kho hiện tại"), eq("Kho đang có 10 sản phẩm.")))
                .thenReturn(LocalDateTime.of(2026, 8, 16, 9, 0));
        when(allowedTool.executeWithContext(
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.any(ChatRequestContext.class)))
                .thenReturn("{\"warehouseName\":\"Kho A\",\"productCount\":10}");
        doReturn(
                new OpenRouterClient.AiResponse(null, stockCall),
                new OpenRouterClient.AiResponse("Kho đang có 10 sản phẩm.", null)
        ).when(openRouterClient).complete(
                anyList(), eq(allowedTools), org.mockito.ArgumentMatchers.any(Duration.class));

        service.processTenantMessage(
                userId,
                new SendMessageRequest(null, "Xem tồn kho kho hiện tại", warehouseId)
        );

        ArgumentCaptor<ChatRequestContext> contextCaptor =
                ArgumentCaptor.forClass(ChatRequestContext.class);
        verify(allowedTool).executeWithContext(
                eq(Map.of()), contextCaptor.capture());
        assertEquals(userId, contextCaptor.getValue().userId());
        assertEquals(warehouseId, contextCaptor.getValue().activeWarehouseId());
    }

    @Test
    void guestStreamPersistsExactlyTheVisibleCompletedReply() {
        configureStreamRuntime();
        UUID sessionId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        LocalDateTime timestamp = LocalDateTime.of(2026, 7, 28, 16, 0);
        List<ChatTool> noTools = List.of();
        when(conversationStore.prepareGuestSession(null))
                .thenReturn(new PreparedChatSession(sessionId, token, List.of()));
        when(toolRegistry.getToolsForRole("GUEST")).thenReturn(noTools);
        when(promptBuilder.buildSystemPrompt(
                eq("GUEST"), eq(noTools), any(ChatRequestContext.class)))
                .thenReturn("system prompt");
        when(conversationStore.appendGuestTurn(
                token, sessionId, "Xin chào", "Chào bạn"))
                .thenReturn(timestamp);
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> chunks = invocation.getArgument(3);
            chunks.accept("Chào ");
            chunks.accept("bạn");
            return new OpenRouterClient.AiResponse("Chào bạn", null);
        }).when(openRouterClient).completeStreaming(
                anyList(),
                eq(noTools),
                any(Duration.class),
                any(Consumer.class),
                any(BooleanSupplier.class)
        );

        assertNotNull(service.streamGuestMessage(
                null,
                new SendMessageRequest(null, "Xin chào")
        ));

        verify(conversationStore).appendGuestTurn(
                token,
                sessionId,
                "Xin chào",
                "Chào bạn"
        );
    }

    @Test
    void streamFailureAfterPartialDeltaDoesNotPersistPartialTurn() {
        configureStreamRuntime();
        UUID sessionId = UUID.randomUUID();
        String token = UUID.randomUUID().toString();
        List<ChatTool> noTools = List.of();
        when(conversationStore.prepareGuestSession(null))
                .thenReturn(new PreparedChatSession(sessionId, token, List.of()));
        when(toolRegistry.getToolsForRole("GUEST")).thenReturn(noTools);
        when(promptBuilder.buildSystemPrompt(
                eq("GUEST"), eq(noTools), any(ChatRequestContext.class)))
                .thenReturn("system prompt");
        org.mockito.Mockito.doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<String> chunks = invocation.getArgument(3);
            chunks.accept("Phản hồi dở");
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
        }).when(openRouterClient).completeStreaming(
                anyList(),
                eq(noTools),
                any(Duration.class),
                any(Consumer.class),
                any(BooleanSupplier.class)
        );

        assertNotNull(service.streamGuestMessage(
                null,
                new SendMessageRequest(null, "Xin chào")
        ));

        verify(conversationStore, never()).appendGuestTurn(
                any(),
                any(),
                any(),
                any()
        );
    }

    private ChatTool namedTool(String name) {
        ChatTool tool = mock(ChatTool.class);
        when(tool.getName()).thenReturn(name);
        return tool;
    }
}
