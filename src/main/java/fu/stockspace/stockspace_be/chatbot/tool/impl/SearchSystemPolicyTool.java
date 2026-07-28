package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.client.EmbeddingClient;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.chatbot.repository.SystemKnowledgeRepository;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool: searchSystemPolicy
 * Tra cứu chính sách, quy định, điều khoản hợp đồng và FAQ của hệ thống (Knowledge Base RAG).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchSystemPolicyTool implements ChatTool {

    private final SystemKnowledgeRepository knowledgeRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() { return "searchSystemPolicy"; }

    @Override
    public String getDescription() {
        return "Tra cứu quy định, chính sách hệ thống, quy trình thuê kho, điều khoản đặt cọc, " +
               "quy định hủy hợp đồng, bảo hiểm & đền bù tài sản. Dùng khi user hỏi về quy định/chính sách.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "query", Map.of("type", "STRING", "description", "Từ khóa hoặc câu hỏi về quy định/chính sách cần tra cứu"),
                        "category", Map.of("type", "STRING", "description", "Phân loại tùy chọn: POLICY, FAQ, CANCELLATION, INSURANCE, RENTAL_PROCESS")
                ),
                "required", List.of("query")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            String query = (String) params.get("query");
            String category = (String) params.get("category");

            if (query == null || query.isBlank()) {
                return "{\"error\": \"Thiếu từ khóa tra cứu chính sách\"}";
            }

            List<SystemKnowledge> allKnowledge = knowledgeRepository.findByCategoryNotDeleted(
                    (category != null && !category.isBlank()) ? category : null
            );

            if (allKnowledge.isEmpty()) {
                allKnowledge = knowledgeRepository.findAllNotDeleted();
            }

            if (allKnowledge.isEmpty()) {
                return "{\"message\": \"Chưa có dữ liệu chính sách trong hệ thống.\"}";
            }

            // Tính toán Cosine Similarity
            List<Float> queryVector = embeddingClient.getEmbedding(query);
            List<ScoredKnowledge> scoredList = new ArrayList<>();

            for (SystemKnowledge item : allKnowledge) {
                double score = 0.0;
                if (!queryVector.isEmpty() && item.getEmbeddingStr() != null && !item.getEmbeddingStr().isBlank()) {
                    List<Float> itemVector = parseVector(item.getEmbeddingStr());
                    score = cosineSimilarity(queryVector, itemVector);
                } else {
                    // Fallback keyword matching
                    score = keywordMatchScore(query, item.getTitle() + " " + item.getContent());
                }
                scoredList.add(new ScoredKnowledge(item, score));
            }

            // Sắp xếp theo score giảm dần, lấy top 3
            scoredList.sort((a, b) -> Double.compare(b.score(), a.score()));
            List<Map<String, Object>> results = scoredList.stream()
                    .limit(3)
                    .map(sk -> Map.<String, Object>of(
                            "category", sk.knowledge().getCategory(),
                            "title", sk.knowledge().getTitle(),
                            "content", sk.knowledge().getContent(),
                            "relevanceScore", String.format("%.2f", sk.score())
                    ))
                    .toList();

            return objectMapper.writeValueAsString(Map.of(
                    "query", query,
                    "policies", results
            ));

        } catch (Exception e) {
            log.error("[SearchSystemPolicyTool] Error: {}", e.getMessage(), e);
            return "{\"error\": \"Không thể tra cứu chính sách lúc này.\"}";
        }
    }

    private record ScoredKnowledge(SystemKnowledge knowledge, double score) {}

    private double cosineSimilarity(List<Float> v1, List<Float> v2) {
        if (v1.isEmpty() || v2.isEmpty() || v1.size() != v2.size()) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += v1.get(i) * v1.get(i);
            normB += v2.get(i) * v2.get(i);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double keywordMatchScore(String query, String text) {
        if (text == null || query == null) return 0.0;
        String textLower = text.toLowerCase();
        String[] words = query.toLowerCase().split("\\s+");
        int matches = 0;
        for (String w : words) {
            if (w.length() > 2 && textLower.contains(w)) {
                matches++;
            }
        }
        return (double) matches / Math.max(words.length, 1);
    }

    private List<Float> parseVector(String str) {
        if (str == null || !str.startsWith("[")) return List.of();
        try {
            String clean = str.substring(1, str.length() - 1).trim();
            if (clean.isEmpty()) return List.of();
            String[] parts = clean.split(",");
            List<Float> list = new ArrayList<>(parts.length);
            for (String p : parts) {
                list.add(Float.parseFloat(p.trim()));
            }
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }
}
