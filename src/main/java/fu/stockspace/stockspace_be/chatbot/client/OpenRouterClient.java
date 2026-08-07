package fu.stockspace.stockspace_be.chatbot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenRouter client using the OpenAI-compatible Chat Completions protocol.
 *
 * <p>The caller owns the conversation transcript. Every tool continuation must
 * contain the assistant tool-call message, its tool result, and the same tool
 * declarations so multi-step workflows remain valid.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterClient {

    private static final Set<String> JSON_SCHEMA_TYPES = Set.of(
            "object", "array", "string", "number", "integer", "boolean", "null"
    );
    private static final Pattern COMPLETE_XML_TOOL_CALL = Pattern.compile(
            "^\\s*<tool_call>\\s*(.*?)\\s*</tool_call>\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern XML_FUNCTION = Pattern.compile(
            "<function(?:\\s+name\\s*=|\\s*=)[\"']?([A-Za-z][A-Za-z0-9_]{0,63})[\"']?\\s*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern XML_PARAMETER = Pattern.compile(
            "<parameter(?:\\s+name\\s*=|\\s*=)[\"']?([A-Za-z][A-Za-z0-9_]{0,63})[\"']?\\s*>\\s*(.*?)\\s*</parameter>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TOOL_NAME =
            Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private static final int MAX_XML_TOOL_CALL_LENGTH = 8_192;
    private static final int MAX_STREAM_TEXT_LENGTH = 65_536;
    private static final int MAX_STREAM_TOOL_ARGUMENTS_LENGTH = 65_536;
    private static final int MAX_STREAM_TOOL_CALL_ID_LENGTH = 256;
    private static final int MAX_STREAM_PAYLOAD_LENGTH = 4 * 1_024 * 1_024;
    private static final int MAX_STREAM_EVENTS = 50_000;
    private static final Duration CANCELLATION_POLL_INTERVAL =
            Duration.ofMillis(50);
    private static final ParameterizedTypeReference<ServerSentEvent<String>>
            STRING_SSE_TYPE = new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.openrouter.api-key:}")
    private String apiKey;

    @Value("${app.openrouter.model:}")
    private String model;

    @Value("${app.openrouter.max-tokens:1024}")
    private int maxTokens;

    @Value("${app.openrouter.temperature:0.2}")
    private double temperature;

    @Value("${app.openrouter.request-timeout:35s}")
    private Duration requestTimeout;

    @Value("${app.openrouter.bulkhead-wait:250ms}")
    private Duration bulkheadWait;

    @Value("${app.openrouter.max-concurrent-requests:12}")
    private int maxConcurrentRequests;

    @Value("${app.openrouter.data-collection:deny}")
    private String dataCollection;

    @Value("${app.openrouter.zdr:true}")
    private boolean zeroDataRetention;

    private Semaphore requestSlots;

    public record AiResponse(String text, FunctionCall functionCall) {
        public boolean isFunctionCall() {
            return functionCall != null;
        }
    }

    public record FunctionCall(String callId, String name, Map<String, Object> args) {
    }

    @PostConstruct
    void initializeBulkhead() {
        requestSlots = new Semaphore(Math.max(1, maxConcurrentRequests), true);
    }

    /**
     * Starts a chat request from persisted history.
     */
    public AiResponse chatWithTools(List<Map<String, Object>> history,
                                    String systemPrompt,
                                    String userMessage,
                                    List<ChatTool> tools) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(buildContent("user", userMessage));
        return complete(messages, tools);
    }

    /**
     * Completes an already-built OpenAI-compatible transcript.
     */
    public AiResponse complete(List<Map<String, Object>> messages, List<ChatTool> tools) {
        return complete(messages, tools, requestTimeout);
    }

    /**
     * Completes a transcript while respecting a caller-level remaining budget.
     */
    public AiResponse complete(List<Map<String, Object>> messages,
                               List<ChatTool> tools,
                               Duration remainingBudget) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Conversation must not be empty");
        }
        return callApi(
                buildRequestBody(messages, tools),
                effectiveRequestTimeout(remainingBudget)
        );
    }

    /**
     * Streams one OpenAI-compatible completion while still returning the
     * assembled response required by the agent loop and persistence layer.
     *
     * <p>Only user-visible assistant text is sent to {@code onTextDelta}.
     * Standard and compatibility tool calls are accumulated server-side and
     * are never exposed to the downstream client.</p>
     */
    public AiResponse completeStreaming(List<Map<String, Object>> messages,
                                        List<ChatTool> tools,
                                        Duration remainingBudget,
                                        Consumer<String> onTextDelta,
                                        BooleanSupplier cancelled) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Conversation must not be empty");
        }

        Map<String, Object> requestBody = buildRequestBody(messages, tools);
        requestBody.put("stream", true);
        return callStreamingApi(
                requestBody,
                effectiveRequestTimeout(remainingBudget),
                onTextDelta == null ? ignored -> {
                } : onTextDelta,
                cancelled == null ? () -> false : cancelled
        );
    }

    /**
     * Backward-compatible helper for a single continuation.
     * New agent loops should append messages themselves and call {@link #complete}.
     */
    public AiResponse sendToolResult(List<Map<String, Object>> conversation,
                                     FunctionCall functionCall,
                                     String toolResult) {
        return sendToolResult(conversation, functionCall, toolResult, List.of());
    }

    public AiResponse sendToolResult(List<Map<String, Object>> conversation,
                                     FunctionCall functionCall,
                                     String toolResult,
                                     List<ChatTool> tools) {
        List<Map<String, Object>> updatedMessages = new ArrayList<>(conversation);
        updatedMessages.add(buildAssistantToolCall(functionCall));
        updatedMessages.add(buildToolResult(functionCall, toolResult));
        return complete(updatedMessages, tools);
    }

    public Map<String, Object> buildAssistantToolCall(FunctionCall functionCall) {
        Map<String, Object> assistantMessage = new LinkedHashMap<>();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", null);
        assistantMessage.put("tool_calls", List.of(Map.of(
                "id", functionCall.callId(),
                "type", "function",
                "function", Map.of(
                        "name", functionCall.name(),
                        "arguments", serializeArgs(functionCall.args())
                )
        )));
        return assistantMessage;
    }

    public Map<String, Object> buildToolResult(FunctionCall functionCall, String toolResult) {
        return Map.of(
                "role", "tool",
                "tool_call_id", functionCall.callId(),
                "name", functionCall.name(),
                "content", toolResult == null ? "" : toolResult
        );
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages,
                                                  List<ChatTool> tools) {
        ensureConfigured();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.trim());
        body.put("messages", messages);
        body.put("temperature", Math.max(0.0, Math.min(2.0, temperature)));
        body.put("max_tokens", Math.max(1, maxTokens));
        body.put("provider", Map.of(
                "data_collection", normalizeDataCollection(dataCollection),
                "zdr", zeroDataRetention
        ));

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", buildToolsPayload(tools));
            body.put("tool_choice", "auto");
            // This implementation intentionally executes one tool at a time.
            body.put("parallel_tool_calls", false);
        }
        return body;
    }

    private List<Map<String, Object>> buildToolsPayload(List<ChatTool> tools) {
        return tools.stream().map(tool -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", normalizeParameters(tool.getParameterSchema()));
            return Map.<String, Object>of("type", "function", "function", function);
        }).toList();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> normalizeParameters(Map<String, Object> schema) {
        Map<String, Object> normalized;
        if (schema == null || schema.isEmpty()) {
            normalized = new LinkedHashMap<>();
            normalized.put("type", "object");
            normalized.put("properties", Map.of());
        } else {
            normalized = (Map<String, Object>) normalizeSchemaValue(schema);
        }
        normalized.putIfAbsent("type", "object");
        normalized.putIfAbsent("properties", Map.of());
        normalized.putIfAbsent("additionalProperties", false);
        return normalized;
    }

    private Object normalizeSchemaValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String stringKey = String.valueOf(key);
                Object normalizedValue = nestedValue;
                if ("type".equals(stringKey) && nestedValue instanceof String type) {
                    String lower = type.toLowerCase(Locale.ROOT);
                    normalizedValue = JSON_SCHEMA_TYPES.contains(lower)
                            ? lower
                            : type;
                } else {
                    normalizedValue = normalizeSchemaValue(nestedValue);
                }
                normalized.put(stringKey, normalizedValue);
            });
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::normalizeSchemaValue).toList();
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private AiResponse callApi(Map<String, Object> requestBody,
                               Duration callTimeout) {
        Semaphore slots = getRequestSlots();
        boolean acquired = false;
        try {
            acquired = slots.tryAcquire(
                    Math.max(1, bulkheadWait.toMillis()),
                    TimeUnit.MILLISECONDS
            );
            if (!acquired) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_BUSY);
            }

            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("HTTP-Referer", "https://stockspace.com")
                    .header("X-Title", "StockSpace AI")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(callTimeout);

            if (response == null) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }
            return parseResponse(response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_BUSY);
        } catch (WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("[OpenRouterClient] Provider HTTP failure status={}", status);
            if (status == 429) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_RATE_LIMITED);
            }
            if (status == 408 || status == 504) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
            }
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
        } catch (WebClientRequestException exception) {
            log.warn("[OpenRouterClient] Provider network failure type={}",
                    exception.getClass().getSimpleName());
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
        } catch (ChatProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            if (isTimeout(exception)) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
            }
            log.error("[OpenRouterClient] Unexpected provider client failure type={}",
                    exception.getClass().getSimpleName());
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private AiResponse callStreamingApi(Map<String, Object> requestBody,
                                        Duration callTimeout,
                                        Consumer<String> onTextDelta,
                                        BooleanSupplier cancelled) {
        if (isCancelled(cancelled)) {
            throw new CancellationException("Chat stream was cancelled");
        }

        Semaphore slots = getRequestSlots();
        boolean acquired = false;
        long deadlineNanos = deadlineAfter(callTimeout);
        try {
            long acquireWaitMillis = Math.min(
                    Math.max(1, bulkheadWait.toMillis()),
                    Math.max(1, remainingDuration(deadlineNanos).toMillis())
            );
            acquired = slots.tryAcquire(acquireWaitMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_BUSY);
            }
            if (isCancelled(cancelled)) {
                throw new CancellationException("Chat stream was cancelled");
            }

            Duration streamBudget = remainingDuration(deadlineNanos);
            if (streamBudget.isZero() || streamBudget.isNegative()) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
            }

            StreamAccumulator accumulator = new StreamAccumulator(onTextDelta);
            Flux<ServerSentEvent<String>> providerEvents = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("HTTP-Referer", "https://stockspace.com")
                    .header("X-Title", "StockSpace AI")
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(STRING_SSE_TYPE);

            Flux<Object> absoluteDeadline = Mono.delay(streamBudget)
                    .flatMapMany(ignored -> Flux.error(
                            new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT)
                    ));
            Flux<Object> cancellationSignal = Flux.interval(
                            Duration.ZERO,
                            CANCELLATION_POLL_INTERVAL
                    )
                    .handle((ignored, sink) -> {
                        if (isCancelled(cancelled)) {
                            sink.error(new CancellationException(
                                    "Chat stream was cancelled"
                            ));
                        }
                    });

            providerEvents
                    .takeUntilOther(Flux.merge(
                            absoluteDeadline,
                            cancellationSignal
                    ))
                    .doOnNext(event -> accumulator.accept(event.data()))
                    .blockLast();

            if (isCancelled(cancelled)) {
                throw new CancellationException("Chat stream was cancelled");
            }
            return accumulator.finish();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Chat stream was interrupted");
        } catch (CancellationException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw mapHttpFailure(exception.getStatusCode().value());
        } catch (WebClientRequestException exception) {
            if (isCancelled(cancelled)) {
                throw new CancellationException("Chat stream was cancelled");
            }
            log.warn("[OpenRouterClient] Streaming provider network failure type={}",
                    exception.getClass().getSimpleName());
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
        } catch (ChatProviderException exception) {
            throw exception;
        } catch (StreamConsumerException exception) {
            throw exception.unwrap();
        } catch (Exception exception) {
            if (isCancelled(cancelled)) {
                throw new CancellationException("Chat stream was cancelled");
            }
            if (isTimeout(exception)) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
            }
            log.error("[OpenRouterClient] Unexpected streaming client failure type={}",
                    exception.getClass().getSimpleName());
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
        } finally {
            if (acquired) {
                slots.release();
            }
        }
    }

    private ChatProviderException mapHttpFailure(int status) {
        log.warn("[OpenRouterClient] Streaming provider HTTP failure status={}", status);
        if (status == 429) {
            return new ChatProviderException(ErrorCode.CHAT_PROVIDER_RATE_LIMITED);
        }
        if (status == 408 || status == 504) {
            return new ChatProviderException(ErrorCode.CHAT_PROVIDER_TIMEOUT);
        }
        return new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
    }

    @SuppressWarnings("unchecked")
    private AiResponse parseResponse(Map<String, Object> response) {
        try {
            if (response.get("error") instanceof Map<?, ?> error) {
                Object code = error.get("code");
                if (code instanceof Number number && number.intValue() == 429) {
                    throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_RATE_LIMITED);
                }
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE);
            }

            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }

            Map<String, Object> choice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) choice.get("message");
            if (message == null) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }

            List<Map<String, Object>> toolCalls =
                    (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                if (toolCalls.size() != 1) {
                    throw new ChatProviderException(
                            ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
                }
                Map<String, Object> rawCall = toolCalls.get(0);
                Map<String, Object> function = (Map<String, Object>) rawCall.get("function");
                if (function == null || !(function.get("name") instanceof String name)
                        || !TOOL_NAME.matcher(name).matches()) {
                    throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
                }
                String callId = rawCall.get("id") instanceof String id && !id.isBlank()
                        ? id
                        : "call_" + UUID.randomUUID();
                return new AiResponse(null, new FunctionCall(
                        callId,
                        name,
                        parseArgs(function.get("arguments"))
                ));
            }

            String text = message.get("content") instanceof String content ? content : "";
            FunctionCall xmlCall = parseXmlToolCall(text);
            if (xmlCall != null) {
                return new AiResponse(null, xmlCall);
            }

            if (text.isBlank()) {
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }
            return new AiResponse(text.trim(), null);
        } catch (ChatProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("[OpenRouterClient] Invalid provider response shape type={}",
                    exception.getClass().getSimpleName());
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(Object argsObject) {
        if (argsObject instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        if (argsObject instanceof String json && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (JsonProcessingException exception) {
                log.warn("[OpenRouterClient] Tool arguments were not valid JSON");
                throw new ChatProviderException(
                        ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }
        }
        return new LinkedHashMap<>();
    }

    private String serializeArgs(Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(args == null ? Map.of() : args);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    /**
     * Compatibility fallback for models which return a complete XML tool call
     * instead of the standardized {@code tool_calls} field. Partial text or a
     * quoted tag is never executed.
     */
    @SuppressWarnings("unchecked")
    private FunctionCall parseXmlToolCall(String text) {
        if (text == null || text.length() > MAX_XML_TOOL_CALL_LENGTH) {
            return null;
        }
        Matcher wrapper = COMPLETE_XML_TOOL_CALL.matcher(text);
        if (!wrapper.matches()) {
            return null;
        }
        String body = wrapper.group(1).trim();

        if (body.startsWith("{")) {
            try {
                Map<String, Object> json = objectMapper.readValue(body, Map.class);
                Object nameValue = json.get("name");
                if (nameValue instanceof String name && TOOL_NAME.matcher(name).matches()) {
                    Object argsValue = json.containsKey("arguments")
                            ? json.get("arguments")
                            : json.get("args");
                    return new FunctionCall(
                            "xml_" + UUID.randomUUID(),
                            name,
                            parseArgs(argsValue)
                    );
                }
            } catch (JsonProcessingException ignored) {
                // Try the tag-shaped compatibility format below.
            }
        }

        Matcher functionMatcher = XML_FUNCTION.matcher(body);
        if (!functionMatcher.find()) {
            return null;
        }
        String functionName = functionMatcher.group(1);
        Map<String, Object> args = new LinkedHashMap<>();
        Matcher parameterMatcher = XML_PARAMETER.matcher(body);
        while (parameterMatcher.find()) {
            args.put(parameterMatcher.group(1), parameterMatcher.group(2).trim());
        }
        return new FunctionCall("xml_" + UUID.randomUUID(), functionName, args);
    }

    private void ensureConfigured() {
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_NOT_CONFIGURED);
        }
    }

    private Semaphore getRequestSlots() {
        if (requestSlots == null) {
            synchronized (this) {
                if (requestSlots == null) {
                    initializeBulkhead();
                }
            }
        }
        return requestSlots;
    }

    private String normalizeDataCollection(String configuredValue) {
        return "allow".equalsIgnoreCase(configuredValue) ? "allow" : "deny";
    }

    private Duration effectiveRequestTimeout(Duration remainingBudget) {
        Duration configured = requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()
                ? Duration.ofSeconds(35)
                : requestTimeout;
        if (remainingBudget == null
                || remainingBudget.isZero()
                || remainingBudget.isNegative()) {
            return configured;
        }
        return remainingBudget.compareTo(configured) < 0
                ? remainingBudget
                : configured;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static Map<String, Object> buildContent(String role, String text) {
        String apiRole = "model".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role)
                ? "assistant"
                : "user";
        return Map.of("role", apiRole, "content", text == null ? "" : text);
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private long deadlineAfter(Duration budget) {
        Duration effective = budget == null || budget.isNegative() || budget.isZero()
                ? requestTimeout
                : budget;
        return System.nanoTime() + effective.toNanos();
    }

    private Duration remainingDuration(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining <= 0 ? Duration.ZERO : Duration.ofNanos(remaining);
    }

    private boolean isCancelled(BooleanSupplier cancelled) {
        return cancelled != null && cancelled.getAsBoolean();
    }

    // -------------------------------------------------------------------------
    // StreamConsumerException — unchecked wrapper used inside Reactor lambdas
    // -------------------------------------------------------------------------

    /**
     * Wraps a {@link ChatProviderException} so it can be thrown from inside a
     * Reactor {@code doOnNext} callback (which only allows unchecked exceptions)
     * and then unwrapped in the surrounding blocking catch block.
     */
    static final class StreamConsumerException extends RuntimeException {

        private final ChatProviderException cause;

        StreamConsumerException(ChatProviderException cause) {
            super(cause.getMessage(), cause, true, false);
            this.cause = cause;
        }

        ChatProviderException unwrap() {
            return cause;
        }
    }

    // -------------------------------------------------------------------------
    // StreamAccumulator — assembles an AiResponse from OpenAI-format SSE frames
    // -------------------------------------------------------------------------

    /**
     * Stateful accumulator that processes one {@code data:} payload at a time
     * from an OpenAI-compatible streaming response.
     *
     * <p>Text deltas are forwarded to {@code onTextDelta} immediately so the
     * SSE coordinator can push them to the browser without waiting for the full
     * response. Tool call fragments are buffered and assembled into a single
     * {@link FunctionCall} on {@link #finish()}.</p>
     *
     * <p>Limits ({@link #MAX_STREAM_TEXT_LENGTH},
     * {@link #MAX_STREAM_TOOL_ARGUMENTS_LENGTH}, etc.) are enforced to prevent
     * runaway allocations from a misbehaving provider.</p>
     */
    private final class StreamAccumulator {

        private final Consumer<String> onTextDelta;

        // Text accumulation
        private final StringBuilder textBuffer = new StringBuilder();
        private int totalTextLength = 0;

        // Tool-call accumulation (one call at a time per OpenAI spec)
        private String toolCallId;
        private String toolCallName;
        private final StringBuilder toolArgBuffer = new StringBuilder();
        private boolean hasToolCall = false;

        // Safety counters
        private int eventCount = 0;
        private int totalPayloadBytes = 0;

        StreamAccumulator(Consumer<String> onTextDelta) {
            this.onTextDelta = onTextDelta == null ? ignored -> {
            } : onTextDelta;
        }

        /**
         * Processes a single raw SSE data string.
         * Called from {@code doOnNext} — must not throw checked exceptions.
         */
        @SuppressWarnings("unchecked")
        void accept(String data) {
            if (data == null || data.isBlank()) {
                return;
            }
            if ("[DONE]".equals(data.trim())) {
                return;
            }
            if (++eventCount > MAX_STREAM_EVENTS) {
                throw new StreamConsumerException(
                        new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE));
            }
            totalPayloadBytes += data.length();
            if (totalPayloadBytes > MAX_STREAM_PAYLOAD_LENGTH) {
                throw new StreamConsumerException(
                        new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE));
            }

            JsonNode root;
            try {
                root = objectMapper.readTree(data);
            } catch (Exception ignored) {
                // Malformed JSON chunk — skip silently; provider may send keep-alive comments.
                return;
            }

            // Provider-level error embedded in a streaming chunk
            if (root.has("error")) {
                JsonNode errorNode = root.get("error");
                int code = errorNode.path("code").asInt(0);
                if (code == 429) {
                    throw new StreamConsumerException(
                            new ChatProviderException(ErrorCode.CHAT_PROVIDER_RATE_LIMITED));
                }
                throw new StreamConsumerException(
                        new ChatProviderException(ErrorCode.CHAT_PROVIDER_UNAVAILABLE));
            }

            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return;
            }

            JsonNode delta = choices.get(0).path("delta");
            if (!delta.isObject()) {
                return;
            }

            // --- Text delta ---
            JsonNode contentNode = delta.path("content");
            if (contentNode.isTextual()) {
                String chunk = contentNode.asText();
                if (!chunk.isEmpty()) {
                    int remaining = MAX_STREAM_TEXT_LENGTH - totalTextLength;
                    if (remaining > 0) {
                        String accepted = chunk.length() <= remaining
                                ? chunk
                                : chunk.substring(0, remaining);
                        textBuffer.append(accepted);
                        totalTextLength += accepted.length();
                        onTextDelta.accept(accepted);
                    }
                }
            }

            // --- Tool-call delta ---
            JsonNode toolCallsNode = delta.path("tool_calls");
            if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                JsonNode tc = toolCallsNode.get(0);
                hasToolCall = true;

                // id and name arrive only in the first chunk
                JsonNode idNode = tc.path("id");
                if (idNode.isTextual() && !idNode.asText().isBlank()) {
                    String rawId = idNode.asText().trim();
                    toolCallId = rawId.length() <= MAX_STREAM_TOOL_CALL_ID_LENGTH
                            ? rawId
                            : rawId.substring(0, MAX_STREAM_TOOL_CALL_ID_LENGTH);
                }

                JsonNode functionNode = tc.path("function");
                if (functionNode.isObject()) {
                    JsonNode nameNode = functionNode.path("name");
                    if (nameNode.isTextual() && !nameNode.asText().isBlank()) {
                        String rawName = nameNode.asText().trim();
                        if (TOOL_NAME.matcher(rawName).matches()) {
                            toolCallName = rawName;
                        }
                    }
                    JsonNode argsNode = functionNode.path("arguments");
                    if (argsNode.isTextual()) {
                        String fragment = argsNode.asText();
                        int remaining = MAX_STREAM_TOOL_ARGUMENTS_LENGTH
                                - toolArgBuffer.length();
                        if (remaining > 0 && !fragment.isEmpty()) {
                            toolArgBuffer.append(
                                    fragment.length() <= remaining
                                            ? fragment
                                            : fragment.substring(0, remaining)
                            );
                        }
                    }
                }
            }
        }

        /**
         * Returns the assembled {@link AiResponse}.
         * Must be called after the stream has been fully consumed.
         */
        AiResponse finish() {
            if (hasToolCall && toolCallName != null
                    && TOOL_NAME.matcher(toolCallName).matches()) {
                String callId = toolCallId != null && !toolCallId.isBlank()
                        ? toolCallId
                        : "call_" + UUID.randomUUID();
                Map<String, Object> args = parseStreamedArgs(toolArgBuffer.toString());
                return new AiResponse(null, new FunctionCall(callId, toolCallName, args));
            }

            String text = textBuffer.toString().trim();
            if (text.isEmpty()) {
                // Some providers omit content when finish_reason=stop with empty reply.
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }
            // XML tool-call fallback — same logic as non-streaming path
            FunctionCall xmlCall = parseXmlToolCall(text);
            if (xmlCall != null) {
                return new AiResponse(null, xmlCall);
            }
            return new AiResponse(text, null);
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> parseStreamedArgs(String json) {
            if (json == null || json.isBlank()) {
                return new LinkedHashMap<>();
            }
            try {
                return objectMapper.readValue(json, Map.class);
            } catch (Exception ignored) {
                log.warn("[StreamAccumulator] Tool arguments were not valid JSON");
                throw new ChatProviderException(ErrorCode.CHAT_PROVIDER_INVALID_RESPONSE);
            }
        }
    }
}

