package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient;
import fu.stockspace.stockspace_be.chatbot.client.OpenRouterClient.AiResponse;
import fu.stockspace.stockspace_be.chatbot.config.AuthenticatedChatRateLimiter;
import fu.stockspace.stockspace_be.chatbot.config.AuthenticatedChatRateLimiter.Permit;
import fu.stockspace.stockspace_be.chatbot.config.ChatStreamRuntime;
import fu.stockspace.stockspace_be.chatbot.dto.ChatMessageResponse;
import fu.stockspace.stockspace_be.chatbot.dto.ChatResponse;
import fu.stockspace.stockspace_be.chatbot.dto.ChatSessionResponse;
import fu.stockspace.stockspace_be.chatbot.dto.ChatStreamEvents;
import fu.stockspace.stockspace_be.chatbot.dto.SendMessageRequest;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatToolRegistry;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;








@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final String TOOL_FAILURE =
            "{\"error\":\"Không thể lấy dữ liệu, vui lòng thử lại sau\"}";
    private static final String TOOL_NOT_ALLOWED =
            "{\"error\":\"Tool không được phép trong phiên này\"}";
    private static final String TOOL_SUBSCRIPTION_REQUIRED =
            "{\"error\":\"T\\u00ednh n\\u0103ng n\\u00e0y y\\u00eau c\\u1ea7u g\\u00f3i d\\u1ecbch v\\u1ee5 \\u0111ang c\\u00f3 hi\\u1ec7u l\\u1ef1c. H\\u00e3y xem danh s\\u00e1ch g\\u00f3i v\\u00e0 \\u0111\\u0103ng k\\u00fd tr\\u01b0\\u1edbc khi s\\u1eed d\\u1ee5ng.\"}";
    private static final String MAX_ITERATIONS_REPLY =
            "Xin lỗi, tôi chưa thể hoàn thành yêu cầu này. Vui lòng diễn đạt ngắn gọn hơn hoặc thử lại sau.";

    private static final String TENANT_ROLE = "ROLE_TENANT";

    private final ChatConversationStore conversationStore;
    private final OpenRouterClient openRouterClient;
    private final ChatToolRegistry toolRegistry;
    private final SubscriptionService subscriptionService;
    private final PromptBuilder promptBuilder;
    private final ActiveWarehouseContextResolver activeWarehouseContextResolver;
    private final AuthenticatedChatRateLimiter authenticatedRateLimiter;
    private final ChatStreamRuntime chatStreamRuntime;

    @Value("${app.chatbot.max-agent-iterations:4}")
    private int maxAgentIterations;

    @Value("${app.chatbot.request-deadline:75s}")
    private Duration requestDeadline;

    @Value("${app.chatbot.max-tool-result-chars:16000}")
    private int maxToolResultChars;

    @Value("${app.chatbot.max-assistant-response-chars:16000}")
    private int maxAssistantResponseChars;

    public ChatResponse processTenantMessage(UUID userId,
                                             SendMessageRequest request) {
        return authenticatedRateLimiter.execute(
                userId,
                () -> processTenantMessageWithinRateLimit(userId, request)
        );
    }

    private ChatResponse processTenantMessageWithinRateLimit(
            UUID userId,
            SendMessageRequest request) {
        ChatRequestContext context = activeWarehouseContextResolver.resolve(
                userId, request.activeWarehouseId());
        String message = normalizeMessage(request.message());
        PreparedChatSession prepared =
                conversationStore.prepareUserSession(userId, request.sessionId());
        List<ChatTool> tools = getTenantTools(userId);
        String systemPrompt = promptBuilder.buildSystemPrompt(TENANT_ROLE, tools, context);

        String reply = runAgenticLoop(
                prepared.history(),
                systemPrompt,
                message,
                tools,
                context
        );
        LocalDateTime timestamp = conversationStore.appendUserTurn(
                userId,
                prepared.sessionId(),
                message,
                reply
        );
        return new ChatResponse(prepared.sessionId(), null, reply, timestamp);
    }

    public ChatResponse processGuestMessage(String sessionToken,
                                            SendMessageRequest request) {
        ChatRequestContext context = ChatRequestContext.guest();
        String message = normalizeMessage(request.message());
        PreparedChatSession prepared =
                conversationStore.prepareGuestSession(sessionToken);
        List<ChatTool> tools = toolRegistry.getToolsForRole("GUEST");
        String systemPrompt = promptBuilder.buildSystemPrompt("GUEST", tools, context);

        String reply = runAgenticLoop(
                prepared.history(),
                systemPrompt,
                message,
                tools,
                context
        );
        LocalDateTime timestamp = conversationStore.appendGuestTurn(
                prepared.guestToken(),
                prepared.sessionId(),
                message,
                reply
        );
        return new ChatResponse(
                prepared.sessionId(),
                prepared.guestToken(),
                reply,
                timestamp
        );
    }






    public SseEmitter streamTenantMessage(UUID userId,
                                          SendMessageRequest request) {
        Permit permit = authenticatedRateLimiter.acquire(userId);
        try {
            String message = normalizeMessage(request.message());
            PreparedChatSession prepared =
                    conversationStore.prepareUserSession(userId, request.sessionId());
            List<ChatTool> tools = getTenantTools(userId);
            ChatRequestContext context = activeWarehouseContextResolver.resolve(
                    userId, request.activeWarehouseId());
            String systemPrompt = promptBuilder.buildSystemPrompt(TENANT_ROLE, tools, context);

            return startStream(
                    userId,
                    context,
                    prepared,
                    message,
                    systemPrompt,
                    tools,
                    request.sessionId() == null || request.sessionId().isBlank(),
                    permit
            );
        } catch (RuntimeException exception) {
            permit.close();
            throw exception;
        }
    }





    public SseEmitter streamGuestMessage(String sessionToken,
                                         SendMessageRequest request) {
        String message = normalizeMessage(request.message());
        PreparedChatSession prepared =
                conversationStore.prepareGuestSession(sessionToken);
        List<ChatTool> tools = toolRegistry.getToolsForRole("GUEST");
        String systemPrompt = promptBuilder.buildSystemPrompt(
                "GUEST", tools, ChatRequestContext.guest());

        return startStream(
                null,
                ChatRequestContext.guest(),
                prepared,
                message,
                systemPrompt,
                tools,
                sessionToken == null || sessionToken.isBlank(),
                null
        );
    }

    public Page<ChatSessionResponse> getMySessions(UUID userId, Pageable pageable) {
        return conversationStore.getMySessions(userId, pageable);
    }

    public List<ChatMessageResponse> getSessionMessages(UUID userId, UUID sessionId) {
        return conversationStore.getSessionMessages(userId, sessionId);
    }

    public void deleteSession(UUID userId, UUID sessionId) {
        conversationStore.deleteSession(userId, sessionId);
    }

    public List<ChatMessageResponse> getGuestHistory(String sessionToken) {
        return conversationStore.getGuestHistory(sessionToken);
    }

    private SseEmitter startStream(UUID userId,
                                   ChatRequestContext context,
                                   PreparedChatSession prepared,
                                   String message,
                                   String systemPrompt,
                                   List<ChatTool> tools,
                                   boolean sessionCreated,
                                   Permit permit) {
        long timeoutMillis = Math.max(
                1_000L,
                chatStreamRuntime.effectiveTimeout().toMillis()
        );
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        StreamCoordinator coordinator = new StreamCoordinator(
                emitter,
                UUID.randomUUID(),
                userId,
                context,
                prepared,
                message,
                systemPrompt,
                tools,
                sessionCreated,
                permit
        );

        emitter.onTimeout(coordinator::timeout);
        emitter.onError(coordinator::transportError);
        emitter.onCompletion(coordinator::transportCompleted);

        try {
            coordinator.attachWorker(chatStreamRuntime.submit(coordinator::run));
        } catch (RejectedExecutionException exception) {
            coordinator.rejectBeforeStart();
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_BUSY);
        }
        return emitter;
    }

    private String runAgenticLoopStreaming(List<Map<String, Object>> history,
                                           String systemPrompt,
                                           String userMessage,
                                           List<ChatTool> allowedTools,
                                           ChatRequestContext context,
                                           Consumer<String> deltaConsumer,
                                           Consumer<String> statusConsumer,
                                           BooleanSupplier cancelled) {
        List<Map<String, Object>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.addAll(history);
        conversation.add(OpenRouterClient.buildContent("user", userMessage));

        Map<String, ChatTool> allowedByName = allowedTools.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChatTool::getName,
                        Function.identity()
                ));

        long deadlineNanos = System.nanoTime()
                + effectiveDeadline().toNanos();
        ensureStreamActive(cancelled);
        AiResponse response = completeStreamingWithinDeadline(
                conversation,
                allowedTools,
                deadlineNanos,
                deltaConsumer,
                cancelled
        );

        int iterations = 0;
        int iterationLimit = Math.max(1, Math.min(8, maxAgentIterations));
        while (response.isFunctionCall() && iterations < iterationLimit) {
            ensureStreamActive(cancelled);
            statusConsumer.accept("retrieving");

            OpenRouterClient.FunctionCall functionCall = response.functionCall();
            conversation.add(openRouterClient.buildAssistantToolCall(functionCall));

            ChatTool tool = allowedByName.get(functionCall.name());
            String toolResult;
            long startedAt = System.nanoTime();
            if (tool == null) {
                log.warn("[AgenticLoop] Rejected non-allowlisted tool name={}",
                        safeToolName(functionCall.name()));
                toolResult = isSubscriptionLocked(functionCall.name(), context)
                        ? TOOL_SUBSCRIPTION_REQUIRED
                        : TOOL_NOT_ALLOWED;
            } else if (!hasRequiredSubscription(tool, context)) {
                log.info("[AgenticLoop] Subscription-gated tool rejected name={}",
                        tool.getName());
                toolResult = TOOL_SUBSCRIPTION_REQUIRED;
            } else {
                try {
                    Map<String, Object> args = functionCall.args() == null
                            ? Map.of()
                            : new LinkedHashMap<>(functionCall.args());
                    toolResult = capToolResult(tool.executeWithContext(args, context));
                    log.info("[AgenticLoop] Tool completed name={} durationMs={}",
                            tool.getName(),
                            Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
                } catch (Exception exception) {
                    log.warn("[AgenticLoop] Tool failed name={} type={}",
                            tool.getName(),
                            exception.getClass().getSimpleName());
                    toolResult = TOOL_FAILURE;
                }
            }

            ensureStreamActive(cancelled);
            conversation.add(openRouterClient.buildToolResult(functionCall, toolResult));
            iterations++;
            statusConsumer.accept("processing");
            response = completeStreamingWithinDeadline(
                    conversation,
                    allowedTools,
                    deadlineNanos,
                    deltaConsumer,
                    cancelled
            );
        }

        ensureStreamActive(cancelled);
        if (response.isFunctionCall()) {
            log.warn("[AgenticLoop] Iteration limit reached count={}", iterations);
            return MAX_ITERATIONS_REPLY;
        }
        return capAssistantResponse(response.text());
    }

    private AiResponse completeStreamingWithinDeadline(
            List<Map<String, Object>> conversation,
            List<ChatTool> tools,
            long deadlineNanos,
            Consumer<String> deltaConsumer,
            BooleanSupplier cancelled) {
        ensureStreamActive(cancelled);
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
        }
        return openRouterClient.completeStreaming(
                conversation,
                tools,
                Duration.ofNanos(remainingNanos),
                deltaConsumer,
                cancelled
        );
    }

    private void ensureStreamActive(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Chat stream cancelled");
        }
    }

    private String runAgenticLoop(List<Map<String, Object>> history,
                                  String systemPrompt,
                                  String userMessage,
                                  List<ChatTool> allowedTools,
                                  ChatRequestContext context) {
        List<Map<String, Object>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.addAll(history);
        conversation.add(OpenRouterClient.buildContent("user", userMessage));

        Map<String, ChatTool> allowedByName = allowedTools.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChatTool::getName,
                        Function.identity()
                ));

        long deadlineNanos = System.nanoTime()
                + effectiveDeadline().toNanos();
        AiResponse response = completeWithinDeadline(
                conversation,
                allowedTools,
                deadlineNanos
        );

        int iterations = 0;
        int iterationLimit = Math.max(1, Math.min(8, maxAgentIterations));
        while (response.isFunctionCall() && iterations < iterationLimit) {
            OpenRouterClient.FunctionCall functionCall = response.functionCall();
            conversation.add(openRouterClient.buildAssistantToolCall(functionCall));

            ChatTool tool = allowedByName.get(functionCall.name());
            String toolResult;
            long startedAt = System.nanoTime();
            if (tool == null) {

                log.warn("[AgenticLoop] Rejected non-allowlisted tool name={}",
                        safeToolName(functionCall.name()));
                toolResult = isSubscriptionLocked(functionCall.name(), context)
                        ? TOOL_SUBSCRIPTION_REQUIRED
                        : TOOL_NOT_ALLOWED;
            } else if (!hasRequiredSubscription(tool, context)) {
                log.info("[AgenticLoop] Subscription-gated tool rejected name={}",
                        tool.getName());
                toolResult = TOOL_SUBSCRIPTION_REQUIRED;
            } else {
                try {
                    Map<String, Object> args = functionCall.args() == null
                            ? Map.of()
                            : new LinkedHashMap<>(functionCall.args());
                    toolResult = capToolResult(tool.executeWithContext(args, context));
                    log.info("[AgenticLoop] Tool completed name={} durationMs={}",
                            tool.getName(),
                            Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
                } catch (Exception exception) {
                    log.warn("[AgenticLoop] Tool failed name={} type={}",
                            tool.getName(),
                            exception.getClass().getSimpleName());
                    toolResult = TOOL_FAILURE;
                }
            }

            conversation.add(openRouterClient.buildToolResult(functionCall, toolResult));
            iterations++;
            response = completeWithinDeadline(
                    conversation,
                    allowedTools,
                    deadlineNanos
            );
        }

        if (response.isFunctionCall()) {
            log.warn("[AgenticLoop] Iteration limit reached count={}", iterations);
            return MAX_ITERATIONS_REPLY;
        }
        return capAssistantResponse(response.text());
    }

    private AiResponse completeWithinDeadline(List<Map<String, Object>> conversation,
                                              List<ChatTool> tools,
                                              long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
        }
        return openRouterClient.complete(
                conversation,
                tools,
                Duration.ofNanos(remainingNanos)
        );
    }

    private String capToolResult(String value) {
        String result = value == null ? TOOL_FAILURE : value;
        int limit = Math.max(1_000, maxToolResultChars);
        if (result.length() <= limit) {
            return result;
        }
        return result.substring(0, limit) + "\n[tool result truncated]";
    }

    private static final java.util.regex.Pattern UUID_PATTERN =
            java.util.regex.Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private String capAssistantResponse(String value) {
        if (value == null || value.isBlank()) {
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
        }
        String reply = sanitizeRawUuid(value.trim());
        int limit = Math.max(1_000, maxAssistantResponseChars);
        return reply.length() <= limit ? reply : reply.substring(0, limit);
    }

    public static String sanitizeRawUuid(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return UUID_PATTERN.matcher(value).replaceAll("");
    }

    private String normalizeMessage(String message) {
        return message == null ? "" : message.strip();
    }

    private List<ChatTool> getTenantTools(UUID userId) {
        List<ChatTool> roleTools = toolRegistry.getToolsForRole(TENANT_ROLE);
        boolean hasActiveSubscription = subscriptionService.hasActiveSubscription(userId);
        if (!hasActiveSubscription) {
            log.info("[AgenticLoop] Tenant {} has no active subscription; WMS chatbot tools are hidden",
                    userId);
        }
        return ChatToolRegistry.filterForActiveSubscription(roleTools, hasActiveSubscription);
    }

    private boolean hasRequiredSubscription(ChatTool tool, ChatRequestContext context) {
        if (!ChatToolRegistry.requiresActiveSubscription(tool.getName())) {
            return true;
        }
        return context != null
                && context.userId() != null
                && subscriptionService.hasActiveSubscription(context.userId());
    }

    private boolean isSubscriptionLocked(String toolName, ChatRequestContext context) {
        return ChatToolRegistry.requiresActiveSubscription(toolName)
                && (context == null
                || context.userId() == null
                || !subscriptionService.hasActiveSubscription(context.userId()));
    }

    private Duration effectiveDeadline() {
        if (requestDeadline == null || requestDeadline.isZero()
                || requestDeadline.isNegative()) {
            return Duration.ofSeconds(75);
        }
        return requestDeadline;
    }

    private String safeToolName(String name) {
        if (name == null) {
            return "<null>";
        }
        return name.replaceAll("[^A-Za-z0-9_-]", "?");
    }

    private final class StreamCoordinator {

        private static final String PROCESSING_MESSAGE =
                "Đang xử lý yêu cầu";
        private static final String RETRIEVING_MESSAGE =
                "Đang tra cứu thông tin";

        private final SseEmitter emitter;
        private final UUID requestId;
        private final UUID userId;
        private final ChatRequestContext context;
        private final PreparedChatSession prepared;
        private final String userMessage;
        private final String systemPrompt;
        private final List<ChatTool> tools;
        private final boolean sessionCreated;
        private final Permit permit;
        private final Object finalizationLock = new Object();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean resourcesReleased = new AtomicBoolean();
        private final AtomicLong sequence = new AtomicLong();
        private final AtomicReference<Future<?>> worker = new AtomicReference<>();
        private final AtomicReference<ScheduledFuture<?>> heartbeat =
                new AtomicReference<>();
        private final StringBuilder streamedReply = new StringBuilder();

        private StreamCoordinator(SseEmitter emitter,
                                  UUID requestId,
                                  UUID userId,
                                  ChatRequestContext context,
                                  PreparedChatSession prepared,
                                  String userMessage,
                                  String systemPrompt,
                                  List<ChatTool> tools,
                                  boolean sessionCreated,
                                  Permit permit) {
            this.emitter = emitter;
            this.requestId = requestId;
            this.userId = userId;
            this.context = context;
            this.prepared = prepared;
            this.userMessage = userMessage;
            this.systemPrompt = systemPrompt;
            this.tools = List.copyOf(tools);
            this.sessionCreated = sessionCreated;
            this.permit = permit;
        }

        private void attachWorker(Future<?> submittedWorker) {
            worker.set(submittedWorker);
            if (cancelled.get()) {
                submittedWorker.cancel(true);
            }
        }

        private void run() {
            try {
                sendRequired("session", new ChatStreamEvents.Session(
                        ChatStreamEvents.VERSION,
                        requestId,
                        prepared.sessionId(),
                        prepared.guestToken(),
                        sessionCreated
                ));
                sendStatus("processing");
                heartbeat.set(chatStreamRuntime.scheduleHeartbeat(this::sendPing));

                String providerReply = runAgenticLoopStreaming(
                        prepared.history(),
                        systemPrompt,
                        userMessage,
                        tools,
                        context,
                        this::sendDelta,
                        this::sendStatus,
                        this::isCancelled
                );
                ensureStreamActive(this::isCancelled);

                String reply;
                if (streamedReply.isEmpty()) {
                    sendDelta(providerReply);
                    reply = streamedReply.toString();
                } else {
                    reply = streamedReply.toString();
                }
                if (reply.isBlank()) {
                    throw new ChatProviderException(
                            ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
                }
                completeSuccessfully(sanitizeRawUuid(reply));
            } catch (CancellationException ignored) {
                cancelWithoutEvent();
            } catch (Throwable failure) {
                fail(failure, false);
            } finally {
                releaseResources();
            }
        }

        private void completeSuccessfully(String reply) {
            LocalDateTime timestamp;
            Throwable persistenceFailure = null;
            synchronized (finalizationLock) {
                if (terminal.get() || cancelled.get()) {
                    return;
                }
                try {
                    if (userId == null) {
                        timestamp = conversationStore.appendGuestTurn(
                                prepared.guestToken(),
                                prepared.sessionId(),
                                userMessage,
                                reply
                        );
                    } else {
                        timestamp = conversationStore.appendUserTurn(
                                userId,
                                prepared.sessionId(),
                                userMessage,
                                reply
                        );
                    }
                    terminal.set(true);
                } catch (Throwable failure) {
                    timestamp = null;
                    persistenceFailure = failure;
                    cancelled.set(true);
                    terminal.set(true);
                }
            }

            if (persistenceFailure != null) {
                log.error("[ChatStream] Persistence failed requestId={} type={}",
                        requestId,
                        persistenceFailure.getClass().getSimpleName());
                sendErrorBestEffort(ErrorCode.SYSTEM_ERROR);
                emitter.complete();
                return;
            }

            try {
                sendRaw("complete", new ChatStreamEvents.Complete(
                        requestId,
                        prepared.sessionId(),
                        timestamp
                ));
            } catch (IOException exception) {
                log.debug("[ChatStream] Client disconnected after commit requestId={}",
                        requestId);
            } finally {
                emitter.complete();
            }
        }

        private void sendDelta(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }
            ensureStreamActive(this::isCancelled);

            int limit = Math.max(1_000, maxAssistantResponseChars);
            int remaining = limit - streamedReply.length();
            if (remaining <= 0) {
                return;
            }
            String accepted = chunk.length() <= remaining
                    ? chunk
                    : chunk.substring(0, remaining);
            if (accepted.isEmpty()) {
                return;
            }

            sendRequired("delta", new ChatStreamEvents.Delta(
                    requestId,
                    sequence.getAndIncrement(),
                    accepted
            ));
            streamedReply.append(accepted);
        }

        private void sendStatus(String phase) {
            String message = "retrieving".equals(phase)
                    ? RETRIEVING_MESSAGE
                    : PROCESSING_MESSAGE;
            sendRequired("status", new ChatStreamEvents.Status(
                    requestId,
                    phase,
                    message
            ));
        }

        private void sendPing() {
            if (terminal.get() || cancelled.get()) {
                return;
            }
            try {
                sendRaw("ping", new ChatStreamEvents.Ping(
                        requestId,
                        LocalDateTime.now()
                ));
            } catch (IOException exception) {
                cancelWithoutEvent();
            }
        }

        private void sendRequired(String eventName, Object payload) {
            ensureStreamActive(this::isCancelled);
            try {
                sendRaw(eventName, payload);
            } catch (IOException exception) {
                cancelWithoutEvent();
                throw new CancellationException("SSE client disconnected");
            }
        }

        private void sendRaw(String eventName, Object payload) throws IOException {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload, MediaType.APPLICATION_JSON));
        }

        private void timeout() {
            fail(new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT), true);
        }

        private void transportError(Throwable ignored) {
            cancelWithoutEvent();
        }

        private void transportCompleted() {
            if (!terminal.get()) {
                cancelWithoutEvent();
            }
        }

        private void rejectBeforeStart() {
            claimCancellation();
            releaseResources();
            emitter.complete();
        }

        private boolean isCancelled() {
            return cancelled.get() || terminal.get()
                    || Thread.currentThread().isInterrupted();
        }

        private void cancelWithoutEvent() {
            if (!claimCancellation()) {
                return;
            }
            Future<?> activeWorker = worker.get();
            if (activeWorker != null && !activeWorker.isDone()) {
                activeWorker.cancel(true);
            }
            releaseResources();
        }

        private boolean claimCancellation() {
            synchronized (finalizationLock) {
                if (terminal.get()) {
                    return false;
                }
                cancelled.set(true);
                terminal.set(true);
                return true;
            }
        }

        private void fail(Throwable failure, boolean cancelWorker) {
            synchronized (finalizationLock) {
                if (terminal.get()) {
                    return;
                }
                cancelled.set(true);
                terminal.set(true);
            }

            if (cancelWorker) {
                Future<?> activeWorker = worker.get();
                if (activeWorker != null && !activeWorker.isDone()) {
                    activeWorker.cancel(true);
                }
            }

            if (!(failure instanceof CancellationException)) {
                ErrorCode publicError = failure instanceof ChatProviderException provider
                        ? provider.getErrorCode()
                        : ErrorCode.SYSTEM_ERROR;
                if (!(failure instanceof ChatProviderException)) {
                    log.error("[ChatStream] Request failed requestId={} type={}",
                            requestId,
                            failure.getClass().getSimpleName());
                }
                sendErrorBestEffort(publicError);
                emitter.complete();
            }
            releaseResources();
        }

        private void sendErrorBestEffort(ErrorCode errorCode) {
            try {
                sendRaw("error", new ChatStreamEvents.Error(
                        requestId,
                        errorCode.name(),
                        errorCode.getMessage(),
                        isRetryable(errorCode)
                ));
            } catch (IOException ignored) {

            }
        }

        private boolean isRetryable(ErrorCode errorCode) {
            return errorCode == ErrorCode.CHAT_PROVIDER_TIMEOUT
                    || errorCode == ErrorCode.CHAT_PROVIDER_UNAVAILABLE
                    || errorCode == ErrorCode.CHAT_PROVIDER_RATE_LIMITED
                    || errorCode == ErrorCode.CHAT_PROVIDER_BUSY
                    || errorCode == ErrorCode.CHAT_RATE_LIMIT_EXCEEDED;
        }

        private void releaseResources() {
            if (!resourcesReleased.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> scheduledHeartbeat = heartbeat.getAndSet(null);
            if (scheduledHeartbeat != null) {
                scheduledHeartbeat.cancel(false);
            }
            if (permit != null) {
                permit.close();
            }
        }
    }
}
