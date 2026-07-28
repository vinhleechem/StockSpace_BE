package fu.stockspace.stockspace_be.chatbot.client;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client gọi Gemini API với hỗ trợ Function Calling.
 *
 * Flow:
 *   1. chatWithTools()       — gửi history + message + tools → Gemini trả về TEXT hoặc FUNCTION_CALL
 *   2. sendToolResult()      — gửi kết quả tool về Gemini → tiếp tục sinh text
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.model}")
    private String model;

    @Value("${app.gemini.max-output-tokens:1024}")
    private int maxOutputTokens;

    @Value("${app.gemini.temperature:0.7}")
    private double temperature;

    // ── Inner Records ────────────────────────────────────────────────────────

    public record GeminiResponse(String text, FunctionCall functionCall) {
        public boolean isFunctionCall() { return functionCall != null; }
    }

    public record FunctionCall(String name, Map<String, Object> args) {}

    // ── Public Methods ────────────────────────────────────────────────────────

    /**
     * Gửi tin nhắn + history + tools tới Gemini.
     * Trả về TEXT response hoặc FUNCTION_CALL.
     *
     * @param history       Danh sách tin nhắn trước đó (dạng Map với role/parts)
     * @param systemPrompt  System instruction theo role
     * @param userMessage   Tin nhắn user hiện tại
     * @param tools         Danh sách tools được phép dùng
     */
    public GeminiResponse chatWithTools(List<Map<String, Object>> history,
                                        String systemPrompt,
                                        String userMessage,
                                        List<ChatTool> tools) {
        List<Map<String, Object>> contents = new ArrayList<>(history);
        contents.add(buildUserContent(userMessage));

        Map<String, Object> requestBody = buildRequestBody(contents, systemPrompt, tools);
        return callGemini(requestBody);
    }

    /**
     * Gửi kết quả tool trở lại Gemini để sinh câu trả lời cuối.
     *
     * @param conversation  Toàn bộ conversation hiện tại (gồm cả function_call của model)
     * @param toolName      Tên tool vừa thực thi
     * @param toolResult    Kết quả JSON string từ tool
     */
    public GeminiResponse sendToolResult(List<Object> conversation,
                                          String toolName,
                                          String toolResult) {
        List<Object> updated = new ArrayList<>(conversation);
        updated.add(buildFunctionResponseContent(toolName, toolResult));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", updated);
        requestBody.put("generationConfig", buildGenerationConfig());

        return callGemini(requestBody);
    }

    // ── Request Builders ────────────────────────────────────────────────────

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> contents,
                                                  String systemPrompt,
                                                  List<ChatTool> tools) {
        Map<String, Object> body = new HashMap<>();
        body.put("contents", contents);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
        }

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", List.of(
                    Map.of("functionDeclarations", buildFunctionDeclarations(tools))
            ));
        }

        body.put("generationConfig", buildGenerationConfig());
        return body;
    }

    private List<Map<String, Object>> buildFunctionDeclarations(List<ChatTool> tools) {
        return tools.stream().map(tool -> {
            Map<String, Object> decl = new HashMap<>();
            decl.put("name", tool.getName());
            decl.put("description", tool.getDescription());
            decl.put("parameters", tool.getParameterSchema());
            return decl;
        }).toList();
    }

    private Map<String, Object> buildUserContent(String message) {
        return Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", message))
        );
    }

    private Map<String, Object> buildFunctionResponseContent(String toolName, String result) {
        return Map.of(
                "role", "function",
                "parts", List.of(Map.of(
                        "functionResponse", Map.of(
                                "name", toolName,
                                "response", Map.of("result", result)
                        )
                ))
        );
    }

    private Map<String, Object> buildGenerationConfig() {
        return Map.of(
                "maxOutputTokens", maxOutputTokens,
                "temperature", temperature
        );
    }

    // ── Gemini API Call ──────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private GeminiResponse callGemini(Map<String, Object> requestBody) {
        String url = "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        try {
            Map<String, Object> response = webClient.post()
                    .uri(url)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            return parseResponse((Map<String, Object>) response);

        } catch (WebClientResponseException e) {
            log.error("[GeminiClient] HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new InternalServerException(ErrorCode.GEMINI_API_QUOTA_EXCEEDED);
            }
            throw new InternalServerException(ErrorCode.GEMINI_API_ERROR);
        } catch (InternalServerException e) {
            throw e;
        } catch (Exception e) {
            log.error("[GeminiClient] Unexpected error: {}", e.getMessage(), e);
            throw new InternalServerException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    @SuppressWarnings("unchecked")
    private GeminiResponse parseResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("[GeminiClient] No candidates in response");
                return new GeminiResponse("Xin lỗi, tôi không thể xử lý yêu cầu này.", null);
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");

            if (parts == null || parts.isEmpty()) {
                return new GeminiResponse("Xin lỗi, tôi không nhận được phản hồi.", null);
            }

            Map<String, Object> firstPart = parts.get(0);

            // Kiểm tra function call
            if (firstPart.containsKey("functionCall")) {
                Map<String, Object> fc = (Map<String, Object>) firstPart.get("functionCall");
                String name = (String) fc.get("name");
                Map<String, Object> args = (Map<String, Object>) fc.getOrDefault("args", new HashMap<>());
                log.info("[GeminiClient] Function call: {} with args: {}", name, args);
                return new GeminiResponse(null, new FunctionCall(name, args));
            }

            // Text response
            String text = (String) firstPart.getOrDefault("text", "");
            return new GeminiResponse(text, null);

        } catch (Exception e) {
            log.error("[GeminiClient] Failed to parse response: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.GEMINI_API_ERROR);
        }
    }

    // ── Utility: Build history content map ──────────────────────────────────

    /**
     * Tạo content map từ role và text — dùng để build history.
     */
    public static Map<String, Object> buildContent(String role, String text) {
        return Map.of(
                "role", role,
                "parts", List.of(Map.of("text", text))
        );
    }
}
