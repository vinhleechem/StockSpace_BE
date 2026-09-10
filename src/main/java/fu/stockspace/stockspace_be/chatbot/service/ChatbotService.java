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
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;








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
    private static final Pattern CITATION_LABEL = Pattern.compile(
            "\\\"label\\\"\\s*:\\s*\\\"([^\\\"]{1,240})\\\""
    );
    private static final Set<String> SYSTEM_EVIDENCE_MARKERS = Set.of(
            "ton kho", "sku", "san pham", "phieu", "phieu nhap", "phieu xuat", "kiem ke",
            "chuyen kho", "suc chua", "tai trong", "the tich", "dien tich", "kich thuoc",
            "hop dong", "goi dich vu", "goi cua toi", "bao hiem", "dat coc", "tien coc",
            "gia thue", "phi luu kho", "so du", "vi cua toi", "don hang", "xuat hang",
            "nhap hang", "nhap xuat"
    );

    private final ChatConversationStore conversationStore;
    private final OpenRouterClient openRouterClient;
    private final ChatToolRegistry toolRegistry;
    private final SubscriptionService subscriptionService;
    private final PromptBuilder promptBuilder;
    private final ActiveWarehouseContextResolver activeWarehouseContextResolver;
    private final AuthenticatedChatRateLimiter authenticatedRateLimiter;
    private final ChatStreamRuntime chatStreamRuntime;

    @Value("${app.chatbot.max-agent-iterations:6}")
    private int maxAgentIterations = 6;

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
        ChatRequestContext context = resolveRequestContext(userId, request);
        String message = normalizeMessage(request.message());
        PreparedChatSession prepared =
                conversationStore.prepareUserSession(userId, request.sessionId());
        List<ChatTool> tools = getTenantTools(userId);
        String systemPrompt = promptBuilder.buildSystemPrompt(TENANT_ROLE, tools, context);

        AgentRunResult run = runAgenticLoop(
                prepared.history(),
                systemPrompt,
                message,
                tools,
                context,
                prepared.memory()
        );
        String reply = run.reply();
        LocalDateTime timestamp = conversationStore.appendUserTurn(
                userId,
                prepared.sessionId(),
                message,
                reply
        );
        rememberUserContextBestEffort(userId, prepared.sessionId(), run.traces());
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

        AgentRunResult run = runAgenticLoop(
                prepared.history(),
                systemPrompt,
                message,
                tools,
                context,
                prepared.memory()
        );
        String reply = run.reply();
        LocalDateTime timestamp = conversationStore.appendGuestTurn(
                prepared.guestToken(),
                prepared.sessionId(),
                message,
                reply
        );
        rememberGuestContextBestEffort(
                prepared.guestToken(), prepared.sessionId(), run.traces());
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
            ChatRequestContext context = resolveRequestContext(userId, request);
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

    private AgentRunResult runAgenticLoopStreaming(List<Map<String, Object>> history,
                                           String systemPrompt,
                                           String userMessage,
                                           List<ChatTool> allowedTools,
                                           ChatRequestContext context,
                                           ConversationMemory memory,
                                           Consumer<String> deltaConsumer,
                                           Consumer<String> statusConsumer,
                                           BooleanSupplier cancelled) {
        // Rental answers are emitted only after the mandatory evidence gate
        // completes.  Buffering these deltas avoids showing an ungrounded
        // partial answer when a live-rule lookup fails midway through SSE.
        boolean evidenceQuestion = requiresSystemEvidence(userMessage);
        Consumer<String> safeDeltaConsumer = evidenceQuestion ? ignored -> { } : deltaConsumer;
        List<Map<String, Object>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.addAll(history);
        appendMemoryContext(conversation, memory, userMessage);
        appendQueryPlanContext(conversation, userMessage);
        conversation.add(OpenRouterClient.buildContent("user", userMessage));
        List<ToolExecutionTrace> traces = new ArrayList<>();

        Map<String, ChatTool> allowedByName = allowedTools.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChatTool::getName,
                        Function.identity()
                ));

        long deadlineNanos = System.nanoTime()
                + effectiveDeadline().toNanos();
        ensureStreamActive(cancelled);
        preloadWarehouseDimensionLookup(
                conversation, allowedByName, memory, userMessage,
                context, traces, statusConsumer, cancelled);
        preloadWarehouseSearch(
                conversation, allowedByName, userMessage,
                context, traces, statusConsumer, cancelled);
        preloadRentalLookup(
                conversation, allowedByName, userMessage,
                context, traces, statusConsumer, cancelled);
        AiResponse response = completeStreamingWithinDeadline(
                conversation,
                allowedTools,
                deadlineNanos,
                safeDeltaConsumer,
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
            Map<String, Object> args = memory.enrichToolArguments(
                    functionCall.name(),
                    functionCall.args(),
                    userMessage
            );
            boolean successful = false;
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
                    toolResult = capToolResult(tool.executeWithContext(args, context));
                    successful = isSuccessfulToolResult(toolResult);
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

            traces.add(new ToolExecutionTrace(
                    functionCall.name(),
                    args,
                    toolResult,
                    successful,
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
            ));

            ensureStreamActive(cancelled);
            conversation.add(openRouterClient.buildToolResult(functionCall, toolResult));
            iterations++;
            statusConsumer.accept("processing");
            response = completeStreamingWithinDeadline(
                    conversation,
                    allowedTools,
                    deadlineNanos,
                    safeDeltaConsumer,
                    cancelled
            );
        }

        ensureStreamActive(cancelled);
        if (response.isFunctionCall()) {
            log.warn("[AgenticLoop] Iteration limit reached count={}", iterations);
            return new AgentRunResult(MAX_ITERATIONS_REPLY, traces);
        }
        return new AgentRunResult(
                enforceCitations(
                        AnswerEvidenceVerifier.guard(
                                enforceSystemEvidence(
                                        enforceRentalEvidence(
                                                enforceQueryEvidence(
                                                        capAssistantResponse(response.text()),
                                                        userMessage,
                                                        allowedByName,
                                                        traces),
                                                userMessage, allowedByName, traces),
                                        userMessage, traces),
                                traces),
                        traces),
                traces
        );
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

    private AgentRunResult runAgenticLoop(List<Map<String, Object>> history,
                                  String systemPrompt,
                                  String userMessage,
                                  List<ChatTool> allowedTools,
                                  ChatRequestContext context,
                                  ConversationMemory memory) {
        List<Map<String, Object>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.addAll(history);
        appendMemoryContext(conversation, memory, userMessage);
        appendQueryPlanContext(conversation, userMessage);
        conversation.add(OpenRouterClient.buildContent("user", userMessage));
        List<ToolExecutionTrace> traces = new ArrayList<>();

        Map<String, ChatTool> allowedByName = allowedTools.stream()
                .collect(Collectors.toUnmodifiableMap(
                        ChatTool::getName,
                        Function.identity()
                ));

        long deadlineNanos = System.nanoTime()
                + effectiveDeadline().toNanos();
        preloadWarehouseDimensionLookup(
                conversation, allowedByName, memory, userMessage,
                context, traces, null, null);
        preloadWarehouseSearch(
                conversation, allowedByName, userMessage,
                context, traces, null, null);
        preloadRentalLookup(
                conversation, allowedByName, userMessage,
                context, traces, null, null);
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
            Map<String, Object> args = memory.enrichToolArguments(
                    functionCall.name(),
                    functionCall.args(),
                    userMessage
            );
            boolean successful = false;
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
                    toolResult = capToolResult(tool.executeWithContext(args, context));
                    successful = isSuccessfulToolResult(toolResult);
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

            traces.add(new ToolExecutionTrace(
                    functionCall.name(),
                    args,
                    toolResult,
                    successful,
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
            ));

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
            return new AgentRunResult(MAX_ITERATIONS_REPLY, traces);
        }
        return new AgentRunResult(
                enforceCitations(
                        AnswerEvidenceVerifier.guard(
                                enforceSystemEvidence(
                                        enforceRentalEvidence(
                                                enforceQueryEvidence(
                                                        capAssistantResponse(response.text()),
                                                        userMessage,
                                                        allowedByName,
                                                        traces),
                                                userMessage, allowedByName, traces),
                                        userMessage, traces),
                                traces),
                        traces),
                traces
        );
    }

    private void appendMemoryContext(
            List<Map<String, Object>> conversation,
            ConversationMemory memory,
            String userMessage
    ) {
        ConversationMemory safeMemory = memory == null
                ? ConversationMemory.empty()
                : memory;
        String memoryPrompt = safeMemory.promptContext(userMessage);
        if (!memoryPrompt.isBlank()) {
            conversation.add(Map.of("role", "system", "content", memoryPrompt));
        }
    }

    private void appendQueryPlanContext(
            List<Map<String, Object>> conversation,
            String userMessage
    ) {
        String planContext = ChatQueryPlanner.plan(userMessage).promptContext();
        if (!planContext.isBlank()) {
            conversation.add(Map.of("role", "system", "content", planContext));
        }
    }

    private void preloadWarehouseSearch(
            List<Map<String, Object>> conversation,
            Map<String, ChatTool> allowedByName,
            String userMessage,
            ChatRequestContext context,
            List<ToolExecutionTrace> traces,
            Consumer<String> statusConsumer,
            BooleanSupplier cancelled
    ) {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(userMessage);
        if (plan.intent() != ChatQueryPlanner.Intent.WAREHOUSE_SEARCH
                || allowedByName == null || traces == null) {
            return;
        }
        ChatTool tool = allowedByName.get(plan.requiredTool());
        if (tool == null || !hasRequiredSubscription(tool, context)) {
            conversation.add(Map.of(
                    "role", "system",
                    "content", "Đây là yêu cầu tra cứu kho nhưng phiên này chưa có quyền dùng dữ liệu kho. "
                            + "Chỉ được nói rõ chưa thể tra cứu, không được tự suy đoán."
            ));
            return;
        }
        if (cancelled != null) {
            ensureStreamActive(cancelled);
        }
        if (statusConsumer != null) {
            statusConsumer.accept("retrieving");
        }

        Map<String, Object> args = plan.filters();
        OpenRouterClient.FunctionCall functionCall = new OpenRouterClient.FunctionCall(
                "search_" + UUID.randomUUID(), tool.getName(), args);
        conversation.add(openRouterClient.buildAssistantToolCall(functionCall));
        long startedAt = System.nanoTime();
        String toolResult;
        boolean successful = false;
        try {
            toolResult = capToolResult(tool.executeWithContext(args, context));
            successful = isSuccessfulToolResult(toolResult);
        } catch (Exception exception) {
            log.warn("[AgenticLoop] Deterministic warehouse search failed type={}",
                    exception.getClass().getSimpleName());
            toolResult = TOOL_FAILURE;
        }
        traces.add(new ToolExecutionTrace(
                functionCall.name(),
                args,
                toolResult,
                successful,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        ));
        conversation.add(openRouterClient.buildToolResult(functionCall, toolResult));
        if (statusConsumer != null) {
            statusConsumer.accept("processing");
        }
    }

    /**
     * Handles the high-frequency failure mode where a user asks for a field
     * of the warehouse returned in the previous turn. This read-only lookup is
     * deterministic and happens before the model gets a chance to repeat a
     * broad search with the full natural-language question as its keyword.
     */
    private void preloadWarehouseDimensionLookup(
            List<Map<String, Object>> conversation,
            Map<String, ChatTool> allowedByName,
            ConversationMemory memory,
            String userMessage,
            ChatRequestContext context,
            List<ToolExecutionTrace> traces,
            Consumer<String> statusConsumer,
            BooleanSupplier cancelled
    ) {
        if (memory == null || !memory.isDimensionQuestion(userMessage)
                || allowedByName == null || traces == null) {
            return;
        }
        ChatTool tool = allowedByName.get("getPublicWarehouseLayout");
        if (tool == null || !hasRequiredSubscription(tool, context)) {
            return;
        }
        Map<String, Object> args = memory.enrichToolArguments(
                tool.getName(), Map.of(), userMessage);
        Object warehouseId = args.get("warehouseId");
        if (warehouseId == null || warehouseId.toString().isBlank()) {
            return;
        }
        if (cancelled != null) {
            ensureStreamActive(cancelled);
        }
        if (statusConsumer != null) {
            statusConsumer.accept("retrieving");
        }

        OpenRouterClient.FunctionCall functionCall = new OpenRouterClient.FunctionCall(
                "memory_" + UUID.randomUUID(), tool.getName(), args);
        conversation.add(openRouterClient.buildAssistantToolCall(functionCall));
        long startedAt = System.nanoTime();
        String toolResult;
        boolean successful = false;
        try {
            toolResult = capToolResult(tool.executeWithContext(args, context));
            successful = isSuccessfulToolResult(toolResult);
        } catch (Exception exception) {
            log.warn("[AgenticLoop] Deterministic warehouse lookup failed type={}",
                    exception.getClass().getSimpleName());
            toolResult = TOOL_FAILURE;
        }
        traces.add(new ToolExecutionTrace(
                functionCall.name(),
                args,
                toolResult,
                successful,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        ));
        conversation.add(openRouterClient.buildToolResult(functionCall, toolResult));
        if (statusConsumer != null) {
            statusConsumer.accept("processing");
        }
    }

    /**
     * Rental-system questions are grounded before the model is allowed to
     * compose an answer.  This prevents a generic LLM answer from being used
     * for live fees, active rules, package data, or the tenant's own records.
     */
    private void preloadRentalLookup(
            List<Map<String, Object>> conversation,
            Map<String, ChatTool> allowedByName,
            String userMessage,
            ChatRequestContext context,
            List<ToolExecutionTrace> traces,
            Consumer<String> statusConsumer,
            BooleanSupplier cancelled
    ) {
        RentalIntentClassifier.Intent intent = RentalIntentClassifier.classify(userMessage);
        if (intent.route() == RentalIntentClassifier.Route.NONE
                || allowedByName == null || traces == null) {
            return;
        }

        String requiredToolName = intent.requiredTool();
        ChatTool tool = allowedByName.get(requiredToolName);
        if (tool == null || !hasRequiredSubscription(tool, context)) {
            conversation.add(Map.of(
                    "role", "system",
                    "content", rentalRoutingGuard(intent, tool == null)
            ));
            return;
        }

        if (cancelled != null) {
            ensureStreamActive(cancelled);
        }
        if (statusConsumer != null) {
            statusConsumer.accept("retrieving");
        }

        Map<String, Object> args = rentalToolArguments(intent, userMessage);
        OpenRouterClient.FunctionCall functionCall = new OpenRouterClient.FunctionCall(
                "rental_" + UUID.randomUUID(), tool.getName(), args);
        conversation.add(openRouterClient.buildAssistantToolCall(functionCall));
        long startedAt = System.nanoTime();
        String toolResult;
        boolean successful = false;
        try {
            toolResult = capToolResult(tool.executeWithContext(args, context));
            successful = isSuccessfulToolResult(toolResult);
        } catch (Exception exception) {
            log.warn("[AgenticLoop] Deterministic rental lookup failed tool={} type={}",
                    tool.getName(), exception.getClass().getSimpleName());
            toolResult = TOOL_FAILURE;
        }
        traces.add(new ToolExecutionTrace(
                functionCall.name(),
                args,
                toolResult,
                successful,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis()
        ));
        conversation.add(openRouterClient.buildToolResult(functionCall, toolResult));
        if (statusConsumer != null) {
            statusConsumer.accept("processing");
        }
    }

    private Map<String, Object> rentalToolArguments(
            RentalIntentClassifier.Intent intent,
            String userMessage
    ) {
        if (!"searchSystemPolicy".equals(intent.requiredTool())) {
            return Map.of();
        }
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("query", userMessage);
        args.put("topK", 3);
        if (intent.category() != null) {
            args.put("category", intent.category());
        }
        return Map.copyOf(args);
    }

    private String rentalRoutingGuard(
            RentalIntentClassifier.Intent intent,
            boolean toolUnavailable
    ) {
        if (toolUnavailable && (intent.route()
                == RentalIntentClassifier.Route.MY_CONTRACTS
                || intent.route()
                == RentalIntentClassifier.Route.MY_ACTIVE_SUBSCRIPTION)) {
            return "Đây là dữ liệu cá nhân của người thuê. Không có quyền tra cứu trong phiên này; "
                    + "chỉ được hướng dẫn người dùng đăng nhập, không được tự suy đoán.";
        }
        return "Đây là câu hỏi nghiệp vụ thuê kho nhưng chưa lấy được dữ liệu xác minh. "
                + "Chỉ được nói rõ chưa thể kiểm tra lúc này; tuyệt đối không tự trả lời hoặc bịa số liệu.";
    }

    private String enforceRentalEvidence(
            String reply,
            String userMessage,
            Map<String, ChatTool> allowedByName,
            List<ToolExecutionTrace> traces
    ) {
        RentalIntentClassifier.Intent intent = RentalIntentClassifier.classify(userMessage);
        if (intent.route() == RentalIntentClassifier.Route.NONE) {
            return reply;
        }
        boolean verified = traces != null && traces.stream().anyMatch(trace ->
                trace != null
                        && trace.successful()
                        && intent.requiredTool().equals(trace.toolName()));
        if (verified) {
            return reply;
        }
        boolean unavailable = allowedByName == null
                || !allowedByName.containsKey(intent.requiredTool());
        if (unavailable && (intent.route()
                == RentalIntentClassifier.Route.MY_CONTRACTS
                || intent.route()
                == RentalIntentClassifier.Route.MY_ACTIVE_SUBSCRIPTION)) {
            return "Bạn cần đăng nhập để tôi tra cứu dữ liệu thuê kho của riêng bạn.";
        }
        return "Tôi chưa thể xác minh thông tin thuê kho từ dữ liệu hệ thống lúc này, nên không muốn đoán sai. "
                + "Bạn vui lòng thử lại sau.";
    }

    private String enforceQueryEvidence(
            String reply,
            String userMessage,
            Map<String, ChatTool> allowedByName,
            List<ToolExecutionTrace> traces
    ) {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(userMessage);
        if (plan.intent() != ChatQueryPlanner.Intent.WAREHOUSE_SEARCH) {
            return reply;
        }
        boolean verified = traces != null && traces.stream().anyMatch(trace ->
                trace != null && trace.successful()
                        && plan.requiredTool().equals(trace.toolName()));
        if (verified) {
            return reply;
        }
        if (allowedByName == null || !allowedByName.containsKey(plan.requiredTool())) {
            return "Tôi chưa thể tra cứu danh sách kho trong phiên này nên không muốn đoán sai. "
                    + "Bạn vui lòng thử lại sau.";
        }
        return "Tôi chưa thể xác minh kết quả tìm kho từ dữ liệu hệ thống lúc này. "
                + "Bạn vui lòng thử lại sau.";
    }

    private String enforceSystemEvidence(
            String reply,
            String userMessage,
            List<ToolExecutionTrace> traces
    ) {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(userMessage);
        if (plan.intent() != ChatQueryPlanner.Intent.NONE
                || !requiresSystemEvidence(userMessage)) {
            return reply;
        }
        boolean verified = traces != null && traces.stream().anyMatch(trace ->
                trace != null && trace.successful());
        if (verified) {
            return reply;
        }
        return "Tôi chưa thể đọc dữ liệu nghiệp vụ trong phiên này nên không muốn đoán sai. "
                + "Bạn vui lòng thử lại sau.";
    }

    private boolean requiresSystemEvidence(String userMessage) {
        ChatQueryPlanner.Plan plan = ChatQueryPlanner.plan(userMessage);
        if (plan.requiresEvidence()) {
            return true;
        }
        String normalized = SemanticQueryExpansion.normalize(userMessage);
        return SYSTEM_EVIDENCE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private boolean isSuccessfulToolResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return false;
        }
        String normalized = toolResult.stripLeading();
        return !normalized.startsWith("{\"error\"")
                && !normalized.startsWith("{ \"error\"");
    }

    /**
     * Makes provenance visible even when a model forgets to repeat the
     * citation returned by a retrieval tool. This is deliberately additive:
     * the model still controls the explanation, while the backend guarantees a
     * source label is present for every policy lookup.
     */
    private String enforceCitations(String reply, List<ToolExecutionTrace> traces) {
        if (reply == null || traces == null || traces.isEmpty()) {
            return reply;
        }
        java.util.LinkedHashSet<String> labels = new java.util.LinkedHashSet<>();
        for (ToolExecutionTrace trace : traces) {
            if (trace == null || !trace.successful()
                    || !"searchSystemPolicy".equals(trace.toolName())) {
                continue;
            }
            Matcher matcher = CITATION_LABEL.matcher(trace.result());
            while (matcher.find() && labels.size() < 3) {
                String label = matcher.group(1).trim();
                if (!label.isBlank()) {
                    labels.add(label);
                }
            }
        }
        if (labels.isEmpty() || labels.stream().allMatch(reply::contains)) {
            return reply;
        }
        String suffix = "\n\nNguồn tham khảo: " + String.join("; ", labels);
        return capAssistantResponse(reply + suffix);
    }

    private void rememberUserContextBestEffort(
            UUID userId,
            UUID sessionId,
            List<ToolExecutionTrace> traces
    ) {
        if (traces == null || traces.isEmpty()) {
            return;
        }
        try {
            conversationStore.rememberUserToolContext(userId, sessionId, traces);
        } catch (RuntimeException exception) {
            log.warn("[ConversationMemory] Could not persist user context cause={}",
                    exception.getClass().getSimpleName());
        }
    }

    private void rememberGuestContextBestEffort(
            String token,
            UUID sessionId,
            List<ToolExecutionTrace> traces
    ) {
        if (traces == null || traces.isEmpty()) {
            return;
        }
        try {
            conversationStore.rememberGuestToolContext(token, sessionId, traces);
        } catch (RuntimeException exception) {
            log.warn("[ConversationMemory] Could not persist guest context cause={}",
                    exception.getClass().getSimpleName());
        }
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
            log.info("[AgenticLoop] Tenant {} has no active subscription; subscription-gated chatbot tools are hidden",
                    userId);
        }
        return ChatToolRegistry.filterForActiveSubscription(roleTools, hasActiveSubscription);
    }

    private ChatRequestContext resolveRequestContext(UUID userId,
                                                     SendMessageRequest request) {
        if (request == null || request.activeScreen() == null || request.activeScreen().isBlank()) {
            return activeWarehouseContextResolver.resolve(
                    userId, request == null ? null : request.activeWarehouseId());
        }
        return activeWarehouseContextResolver.resolve(
                userId, request.activeWarehouseId(), request.activeScreen());
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

    private record AgentRunResult(
            String reply,
            List<ToolExecutionTrace> traces
    ) {

        private AgentRunResult {
            traces = traces == null ? List.of() : List.copyOf(traces);
        }
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
        /**
         * Holds a possible partial UUID between provider chunks. Sanitizing
         * each chunk independently is not sufficient because a UUID can be
         * split at any character boundary in an SSE response.
         */
        private final StringBuilder pendingReply = new StringBuilder();

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

                AgentRunResult run = runAgenticLoopStreaming(
                        prepared.history(),
                        systemPrompt,
                        userMessage,
                        tools,
                        context,
                        prepared.memory(),
                        this::sendDelta,
                        this::sendStatus,
                        this::isCancelled
                );
                String providerReply = run.reply();
                ensureStreamActive(this::isCancelled);

                String reply;
                if (streamedReply.isEmpty()) {
                    sendDelta(providerReply);
                } else if (providerReply.startsWith(streamedReply)
                        && providerReply.length() > streamedReply.length()) {
                    // The provider text may already have been emitted before
                    // the final citation-enforcement pass. Emit only the
                    // additive suffix so SSE and non-streaming responses stay
                    // identical.
                    sendDelta(providerReply.substring(streamedReply.length()));
                }
                flushPendingDelta();
                reply = streamedReply.toString();
                if (reply.isBlank()) {
                    throw new ChatProviderException(
                            ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
                }
                completeSuccessfully(sanitizeRawUuid(reply), run.traces());
            } catch (CancellationException ignored) {
                cancelWithoutEvent();
            } catch (Throwable failure) {
                fail(failure, false);
            } finally {
                releaseResources();
            }
        }

        private void completeSuccessfully(
                String reply,
                List<ToolExecutionTrace> traces
        ) {
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
                        rememberGuestContextBestEffort(
                                prepared.guestToken(), prepared.sessionId(), traces);
                    } else {
                        timestamp = conversationStore.appendUserTurn(
                                userId,
                                prepared.sessionId(),
                                userMessage,
                                reply
                        );
                        rememberUserContextBestEffort(
                                userId, prepared.sessionId(), traces);
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

            if (streamedReply.length() >= Math.max(1_000, maxAssistantResponseChars)) {
                return;
            }
            pendingReply.append(chunk);
            emitSafePendingPrefix();
        }

        private void flushPendingDelta() {
            if (pendingReply.isEmpty()) {
                return;
            }
            String visible = sanitizeRawUuid(pendingReply.toString());
            pendingReply.setLength(0);
            emitDelta(visible);
        }

        private void emitSafePendingPrefix() {
            String sanitized = sanitizeRawUuid(pendingReply.toString());
            pendingReply.setLength(0);
            pendingReply.append(sanitized);

            int heldLength = longestUuidPrefixSuffix(sanitized);
            int visibleLength = sanitized.length() - heldLength;
            if (visibleLength <= 0) {
                return;
            }
            String visible = sanitized.substring(0, visibleLength);
            pendingReply.delete(0, visibleLength);
            emitDelta(visible);
        }

        private int longestUuidPrefixSuffix(String value) {
            int maximum = Math.min(36, value.length());
            for (int length = maximum; length >= 1; length--) {
                if (isUuidPrefix(value.substring(value.length() - length))) {
                    return length;
                }
            }
            return 0;
        }

        private boolean isUuidPrefix(String value) {
            if (value.isEmpty() || value.length() > 36) {
                return false;
            }
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                boolean hyphenPosition = index == 8 || index == 13 || index == 18 || index == 23;
                if (hyphenPosition) {
                    if (character != '-') {
                        return false;
                    }
                } else if (!isAsciiHexDigit(character)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isAsciiHexDigit(char character) {
            return (character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F');
        }

        private void emitDelta(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return;
            }

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
