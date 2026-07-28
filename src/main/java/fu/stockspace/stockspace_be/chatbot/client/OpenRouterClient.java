package fu.stockspace.stockspace_be.chatbot.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.InternalServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

/**
 * Client gọi OpenRouter API (OpenAI-compatible format) với hỗ trợ Function Calling.
 *
 * Hỗ trợ tất cả các model trên OpenRouter (NVIDIA Nemotron, Meta Llama 3, Qwen 2.5, Gemini...).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenRouterClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.openrouter.api-key}")
    private String apiKey;

    @Value("${app.openrouter.model}")
    private String model;

    @Value("${app.openrouter.max-tokens:1024}")
    private int maxTokens;

    @Value("${app.openrouter.temperature:0.7}")
    private double temperature;

    // ── Inner Records ────────────────────────────────────────────────────────

    public record AiResponse(String text, FunctionCall functionCall) {
        public boolean isFunctionCall() { return functionCall != null; }
    }

    public record FunctionCall(String callId, String name, Map<String, Object> args) {}

    // ── Public Methods ────────────────────────────────────────────────────────

    /**
     * Gửi tin nhắn + history + tools tới OpenRouter AI.
     */
    public AiResponse chatWithTools(List<Map<String, Object>> history,
                                    String systemPrompt,
                                    String userMessage,
                                    List<ChatTool> tools) {
        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        messages.addAll(history);
        messages.add(buildUserContent(userMessage));

        Map<String, Object> requestBody = buildRequestBody(messages, tools);
        return callApi(requestBody);
    }

    /**
     * Gửi kết quả tool trở lại OpenRouter để AI sinh câu trả lời tiếp theo.
     */
    public AiResponse sendToolResult(List<Map<String, Object>> conversation,
                                      FunctionCall functionCall,
                                      String toolResult) {
        List<Map<String, Object>> updatedMessages = new ArrayList<>(conversation);

        // 1. Thêm message của Assistant có chứa tool_calls
        Map<String, Object> assistantMsg = new HashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", null);
        assistantMsg.put("tool_calls", List.of(Map.of(
                "id", functionCall.callId(),
                "type", "function",
                "function", Map.of(
                        "name", functionCall.name(),
                        "arguments", serializeArgs(functionCall.args())
                )
        )));
        updatedMessages.add(assistantMsg);

        // 2. Thêm message kết quả từ tool (role: "tool")
        Map<String, Object> toolMsg = Map.of(
                "role", "tool",
                "tool_call_id", functionCall.callId(),
                "name", functionCall.name(),
                "content", toolResult
        );
        updatedMessages.add(toolMsg);

        Map<String, Object> requestBody = buildRequestBody(updatedMessages, null);
        return callApi(requestBody);
    }

    // ── Request Builders ────────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages,
                                                  List<ChatTool> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", buildToolsPayload(tools));
        }

        return body;
    }

    private List<Map<String, Object>> buildToolsPayload(List<ChatTool> tools) {
        return tools.stream().map(tool -> {
            Map<String, Object> fn = new HashMap<>();
            fn.put("name", tool.getName());
            fn.put("description", tool.getDescription());
            fn.put("parameters", normalizeParameters(tool.getParameterSchema()));

            return Map.of(
                    "type", "function",
                    "function", fn
            );
        }).toList();
    }

    private Map<String, Object> normalizeParameters(Map<String, Object> schema) {
        if (schema == null) return Map.of("type", "object", "properties", Map.of());
        Map<String, Object> normalized = new HashMap<>(schema);
        if ("OBJECT".equals(normalized.get("type"))) {
            normalized.put("type", "object");
        }
        return normalized;
    }

    private Map<String, Object> buildUserContent(String message) {
        return Map.of(
                "role", "user",
                "content", message
        );
    }

    // ── OpenRouter API Call ──────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private AiResponse callApi(Map<String, Object> requestBody) {
        try {
            Map<String, Object> response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://stockspace.com")
                    .header("X-Title", "StockSpace AI")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseResponse((Map<String, Object>) response);

        } catch (WebClientResponseException e) {
            log.error("[OpenRouterClient] HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new InternalServerException(ErrorCode.GEMINI_API_QUOTA_EXCEEDED);
            }
            throw new InternalServerException(ErrorCode.GEMINI_API_ERROR);
        } catch (InternalServerException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OpenRouterClient] Unexpected error: {}", e.getMessage(), e);
            throw new InternalServerException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private AiResponse parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("[OpenRouterClient] No choices in response");
                return new AiResponse("Xin lỗi, tôi không thể xử lý yêu cầu này.", null);
            }

            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

            if (message == null) {
                return new AiResponse("Xin lỗi, tôi không nhận được phản hồi.", null);
            }

            // Kiểm tra tool_calls
            List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) message.get("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                Map<String, Object> firstToolCall = toolCalls.get(0);
                String callId = (String) firstToolCall.get("id");
                Map<String, Object> function = (Map<String, Object>) firstToolCall.get("function");

                String name = (String) function.get("name");
                Object argsObj = function.get("arguments");
                Map<String, Object> args = parseArgs(argsObj);

                log.info("[OpenRouterClient] Function call: {} (id: {}) with args: {}", name, callId, args);
                return new AiResponse(null, new FunctionCall(callId, name, args));
            }

            // Text response — Kiểm tra xem có chứa XML tool_call tag từ Nemotron/DeepSeek không
            String text = (String) message.getOrDefault("content", "");
            if (text != null && text.contains("<tool_call>")) {
                FunctionCall xmlCall = parseXmlToolCall(text);
                if (xmlCall != null) {
                    log.info("[OpenRouterClient] Parsed XML tool call: {} with args: {}", xmlCall.name(), xmlCall.args());
                    return new AiResponse(null, xmlCall);
                }
            }

            return new AiResponse(text, null);

        } catch (Exception e) {
            log.error("[OpenRouterClient] Failed to parse response: {}", e.getMessage(), e);
            throw new InternalServerException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(Object argsObj) {
        if (argsObj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (argsObj instanceof String jsonStr && !jsonStr.isBlank()) {
            try {
                return objectMapper.readValue(jsonStr, Map.class);
            } catch (JsonProcessingException e) {
                log.warn("[OpenRouterClient] Failed to parse args JSON string: {}", jsonStr);
            }
        }
        return new HashMap<>();
    }

    private String serializeArgs(Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(args != null ? args : Map.of());
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private FunctionCall parseXmlToolCall(String text) {
        try {
            java.util.regex.Pattern fnPattern = java.util.regex.Pattern.compile("<function[ =][\"']?([^>\"']+)[\"']?>");
            java.util.regex.Matcher fnMatcher = fnPattern.matcher(text);

            if (!fnMatcher.find()) {
                return null;
            }
            String functionName = fnMatcher.group(1).trim();

            Map<String, Object> args = new HashMap<>();
            java.util.regex.Pattern paramPattern = java.util.regex.Pattern.compile("<parameter[ =][\"']?([^>\"']+)[\"']?>\\s*(.*?)\\s*</parameter>", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher paramMatcher = paramPattern.matcher(text);

            while (paramMatcher.find()) {
                String paramName = paramMatcher.group(1).trim();
                String paramValue = paramMatcher.group(2).trim();
                args.put(paramName, paramValue);
            }

            String callId = "xml_" + UUID.randomUUID().toString().substring(0, 8);
            return new FunctionCall(callId, functionName, args);

        } catch (Exception e) {
            log.warn("[OpenRouterClient] Failed to parse XML tool call: {}", e.getMessage());
            return null;
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    public static Map<String, Object> buildContent(String role, String text) {
        String apiRole = "model".equalsIgnoreCase(role) || "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
        return Map.of(
                "role", apiRole,
                "content", text != null ? text : ""
        );
    }
}
