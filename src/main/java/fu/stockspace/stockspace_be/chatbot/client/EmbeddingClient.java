package fu.stockspace.stockspace_be.chatbot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.InternalServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Client sinh Vector Embedding cho văn bản từ OpenRouter API.
 * Sử dụng model: openai/text-embedding-3-small (1536 chiều).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${app.openrouter.api-key}")
    private String apiKey;

    @Value("${app.openrouter.embedding-model:openai/text-embedding-3-small}")
    private String embeddingModel;

    /**
     * Sinh Vector Embedding từ văn bản đầu vào.
     *
     * @param text Văn bản cần embed (chuỗi không rỗng)
     * @return List<Float> chứa vector embedding (1536 phần tử)
     */
    @SuppressWarnings("unchecked")
    public List<Float> getEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", text.trim()
            );

            Map<String, Object> response = webClient.post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("HTTP-Referer", "https://stockspace.com")
                    .header("X-Title", "StockSpace Embedding")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("data")) {
                log.warn("[EmbeddingClient] Empty response for text: {}", text);
                return new ArrayList<>();
            }

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            if (data == null || data.isEmpty()) {
                return new ArrayList<>();
            }

            List<Number> rawEmbedding = (List<Number>) data.get(0).get("embedding");
            if (rawEmbedding == null) {
                return new ArrayList<>();
            }

            return rawEmbedding.stream()
                    .map(Number::floatValue)
                    .toList();

        } catch (Exception e) {
            log.error("[EmbeddingClient] Error generating embedding: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Format List<Float> sang dạng postgres vector literal: "[0.1, 0.2, 0.3]"
     */
    public String toVectorString(List<Float> vector) {
        if (vector == null || vector.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.size(); i++) {
            sb.append(vector.get(i));
            if (i < vector.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
