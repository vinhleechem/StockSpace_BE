package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.client.EmbeddingClient;
import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.chatbot.repository.PgVectorKnowledgeRepository;
import fu.stockspace.stockspace_be.chatbot.repository.PgVectorKnowledgeRepository.KnowledgeVectorMatch;
import fu.stockspace.stockspace_be.chatbot.repository.SystemKnowledgeRepository;
import fu.stockspace.stockspace_be.chatbot.service.KnowledgeDocumentSupport;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;




@Slf4j
@Component
public class SearchSystemPolicyTool implements ChatTool {

    static final int HARD_MAX_CANDIDATES = 200;
    static final int DEFAULT_TOP_K = 3;
    static final int MAX_TOP_K = 5;
    static final int MAX_QUERY_LENGTH = 500;
    static final double MIN_LEXICAL_SCORE = 0.12;
    static final double MIN_SEMANTIC_ONLY_SCORE = 0.82;

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "do", "for", "how", "i", "is", "of", "or", "the", "to", "what",
            "ai", "ban", "cai", "can", "cho", "co", "cua", "duoc", "gi", "hay", "hoi", "khong",
            "la", "minh", "mot", "nao", "neu", "nhung", "thi", "toi", "va", "ve", "voi", "xin"
    );

    private final SystemKnowledgeRepository knowledgeRepository;
    private final PgVectorKnowledgeRepository vectorKnowledgeRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final int maxCandidates;
    private final int defaultTopK;
    private final double minScore;
    private final boolean pgVectorEnabled;

    public SearchSystemPolicyTool(
            SystemKnowledgeRepository knowledgeRepository,
            PgVectorKnowledgeRepository vectorKnowledgeRepository,
            EmbeddingClient embeddingClient,
            ObjectMapper objectMapper,
            @Value("${app.chatbot.rag.max-candidates:100}") int maxCandidates,
            @Value("${app.chatbot.rag.top-k:3}") int defaultTopK,
            @Value("${app.chatbot.rag.min-score:0.15}") double minScore,
            @Value("${app.chatbot.rag.pgvector.enabled:true}") boolean pgVectorEnabled
    ) {
        this.knowledgeRepository = knowledgeRepository;
        this.vectorKnowledgeRepository = vectorKnowledgeRepository;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.maxCandidates = Math.max(1, Math.min(maxCandidates, HARD_MAX_CANDIDATES));
        this.defaultTopK = Math.max(1, Math.min(defaultTopK, MAX_TOP_K));
        this.minScore = Math.max(0.0, Math.min(minScore, 1.0));
        this.pgVectorEnabled = pgVectorEnabled;
    }

    @Override
    public String getName() {
        return "searchSystemPolicy";
    }

    @Override
    public String getDescription() {
        return "Tra cứu quy trình và thông tin chính sách đã được lưu trong cơ sở tri thức StockSpace. " +
                "Kết quả có nội dung và điểm liên quan; không dùng kết quả để tự suy diễn điều khoản pháp lý.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "Câu hỏi hoặc từ khóa cần tra cứu",
                                "maxLength", MAX_QUERY_LENGTH
                        ),
                        "category", Map.of(
                                "type", "string",
                                "description", "Nhóm tài liệu tùy chọn",
                                "enum", supportedCategoryLabels()
                        ),
                        "topK", Map.of(
                                "type", "integer",
                                "description", "Số kết quả, tối đa " + MAX_TOP_K,
                                "minimum", 1,
                                "maximum", MAX_TOP_K
                        )
                ),
                "required", List.of("query"),
                "additionalProperties", false
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            String query = stringParam(params, "query");
            if (query == null) {
                return error("Thiếu từ khóa tra cứu chính sách.");
            }
            if (query.length() > MAX_QUERY_LENGTH) {
                return error("Từ khóa tra cứu vượt quá " + MAX_QUERY_LENGTH + " ký tự.");
            }

            CategorySelection categorySelection = parseCategory(stringParam(params, "category"));
            if (!categorySelection.valid()) {
                return error(
                        "Nhóm tài liệu không hợp lệ. Các lựa chọn hỗ trợ: " +
                                String.join(", ", supportedCategoryLabels())
                );
            }

            int topK = topK(params == null ? null : params.get("topK"));
            Set<String> queryTerms = terms(query);
            if (queryTerms.isEmpty()) {
                return emptyResult(query, categorySelection.category(), "Không tìm thấy từ khóa có ý nghĩa để tra cứu.");
            }

            List<SystemKnowledge> lexicalCandidates = knowledgeRepository.findSearchCandidates(
                    categorySelection.category(),
                    PageRequest.of(0, maxCandidates)
            );

            List<Float> queryVector = safeQueryEmbedding(query);
            float[] pgQueryVector = toPgVector(queryVector);
            List<KnowledgeVectorMatch> semanticMatches = List.of();
            if (pgVectorEnabled && pgQueryVector != null) {
                try {
                    semanticMatches = vectorKnowledgeRepository.findNearest(
                            pgQueryVector,
                            embeddingClient.getEmbeddingModel(),
                            categorySelection.category(),
                            maxCandidates
                    );
                } catch (RuntimeException exception) {

                    log.warn(
                            "[SearchSystemPolicyTool] pgvector retrieval unavailable; using lexical fallback (cause={})",
                            exception.getClass().getSimpleName()
                    );
                }
            }

            Map<UUID, SystemKnowledge> candidatesById = new LinkedHashMap<>();
            lexicalCandidates.forEach(candidate -> candidatesById.put(candidate.getId(), candidate));
            Map<UUID, Double> semanticScores = new LinkedHashMap<>();
            String semanticBackend = null;
            for (KnowledgeVectorMatch match : semanticMatches) {
                SystemKnowledge knowledge = match.knowledge();
                if (!hasCompatibleEmbedding(knowledge, categorySelection.category())
                        || !Double.isFinite(match.similarity())) {
                    continue;
                }
                candidatesById.putIfAbsent(knowledge.getId(), knowledge);
                semanticScores.merge(
                        knowledge.getId(),
                        Math.max(0.0, Math.min(1.0, match.similarity())),
                        Math::max
                );
            }
            boolean usedPgVector = !semanticScores.isEmpty();
            boolean usedLegacyText = false;
            if (pgQueryVector != null) {





                for (SystemKnowledge candidate : lexicalCandidates) {
                    if (semanticScores.containsKey(candidate.getId())) {
                        continue;
                    }
                    if (!hasCompatibleLegacyEmbedding(
                            candidate,
                            categorySelection.category()
                    )) {
                        continue;
                    }
                    double similarity = cosineSimilarity(
                            queryVector,
                            KnowledgeDocumentSupport.parseVector(candidate.getEmbeddingStr())
                    );
                    if (similarity > 0.0) {
                        semanticScores.put(candidate.getId(), similarity);
                        usedLegacyText = true;
                    }
                }
            }
            if (usedPgVector && usedLegacyText) {
                semanticBackend = "pgvector+legacy-text";
            } else if (usedPgVector) {
                semanticBackend = "pgvector";
            } else if (usedLegacyText) {
                semanticBackend = "legacy-text";
            }

            if (candidatesById.isEmpty()) {
                return emptyResult(query, categorySelection.category(), "Chưa có tài liệu phù hợp trong cơ sở tri thức.");
            }

            boolean usedSemantic = !semanticScores.isEmpty();
            List<ScoredKnowledge> scored = new ArrayList<>();

            for (SystemKnowledge candidate : candidatesById.values()) {
                double lexicalScore = lexicalScore(query, queryTerms, candidate);
                double semanticScore = semanticScores.getOrDefault(candidate.getId(), 0.0);
                boolean semanticUsable = semanticScores.containsKey(candidate.getId());

                double hybridScore = semanticUsable
                        ? Math.max(lexicalScore, 0.65 * semanticScore + 0.35 * lexicalScore)
                        : lexicalScore;
                boolean relevant = lexicalScore >= MIN_LEXICAL_SCORE
                        || (queryTerms.size() >= 2 && semanticScore >= MIN_SEMANTIC_ONLY_SCORE);
                if (relevant && hybridScore >= minScore) {
                    scored.add(new ScoredKnowledge(candidate, hybridScore, lexicalScore, semanticScore));
                }
            }

            scored.sort((left, right) -> {
                int scoreOrder = Double.compare(right.score(), left.score());
                if (scoreOrder != 0) {
                    return scoreOrder;
                }
                int lexicalOrder = Double.compare(right.lexicalScore(), left.lexicalScore());
                if (lexicalOrder != 0) {
                    return lexicalOrder;
                }
                return safeId(left.knowledge()).compareTo(safeId(right.knowledge()));
            });

            List<Map<String, Object>> policies = scored.stream()
                    .limit(topK)
                    .map(this::toResult)
                    .toList();

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("query", query);
            if (categorySelection.category() != null) {
                response.put("category", categoryLabel(categorySelection.category()));
            }
            response.put("retrievalMode", usedSemantic ? "hybrid" : "lexical");
            if (usedSemantic) {
                response.put("vectorStore", semanticBackend);
            }
            response.put("policies", policies);
            if (policies.isEmpty()) {
                response.put("message", "Không tìm thấy tài liệu đủ liên quan; hệ thống không trả kết quả phỏng đoán.");
            }
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {

            log.error(
                    "[SearchSystemPolicyTool] Retrieval failed (cause={})",
                    exception.getClass().getSimpleName()
            );
            return error("Không thể tra cứu chính sách lúc này.");
        }
    }

    private CategorySelection parseCategory(String rawCategory) {
        if (rawCategory == null) {
            return new CategorySelection(null, true);
        }
        String normalizedCategory = normalize(rawCategory);
        return Arrays.stream(KnowledgeCategory.values())
                .filter(category -> normalize(categoryLabel(category)).equals(normalizedCategory))
                .findFirst()


                .or(() -> KnowledgeCategory.fromExternalValue(rawCategory))
                .map(category -> new CategorySelection(category, true))
                .orElseGet(() -> new CategorySelection(null, false));
    }

    private List<String> supportedCategoryLabels() {
        return Arrays.stream(KnowledgeCategory.values())
                .map(this::categoryLabel)
                .toList();
    }

    private String categoryLabel(KnowledgeCategory category) {
        return switch (category) {
            case POLICY -> "Chính sách";
            case FAQ -> "Câu hỏi thường gặp";
            case CANCELLATION -> "Hủy hợp đồng";
            case INSURANCE -> "Bảo hiểm và đền bù";
            case RENTAL_PROCESS -> "Quy trình thuê kho";
        };
    }

    private int topK(Object rawTopK) {
        if (rawTopK == null) {
            return defaultTopK;
        }
        try {
            int value = rawTopK instanceof Number number
                    ? number.intValue()
                    : Integer.parseInt(rawTopK.toString());
            return Math.max(1, Math.min(value, MAX_TOP_K));
        } catch (NumberFormatException exception) {
            return defaultTopK;
        }
    }

    private List<Float> safeQueryEmbedding(String query) {
        try {
            return embeddingClient.getEmbedding(query);
        } catch (RuntimeException exception) {
            log.warn(
                    "[SearchSystemPolicyTool] Query embedding unavailable; using lexical fallback (cause={})",
                    exception.getClass().getSimpleName()
            );
            return List.of();
        }
    }

    private float[] toPgVector(List<Float> vector) {
        if (vector == null
                || vector.size() != SystemKnowledge.EMBEDDING_DIMENSIONS
                || embeddingClient.getEmbeddingDimensions() != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            return null;
        }

        float[] result = new float[vector.size()];
        double normSquared = 0.0;
        for (int index = 0; index < vector.size(); index++) {
            Float value = vector.get(index);
            if (value == null || !Float.isFinite(value)) {
                return null;
            }
            result[index] = value;
            normSquared += (double) value * value;
        }
        return normSquared > 0.0 && Double.isFinite(normSquared) ? result : null;
    }

    private boolean hasCompatibleEmbedding(
            SystemKnowledge knowledge,
            KnowledgeCategory requestedCategory
    ) {
        if (!hasCompatibleMetadata(knowledge, requestedCategory)) {
            return false;
        }

        float[] embedding = knowledge.getEmbeddingVector();
        if (embedding == null || embedding.length != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            return false;
        }
        double normSquared = 0.0;
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                return false;
            }
            normSquared += (double) value * value;
        }
        return normSquared > 0.0 && Double.isFinite(normSquared);
    }

    private boolean hasCompatibleLegacyEmbedding(
            SystemKnowledge knowledge,
            KnowledgeCategory requestedCategory
    ) {
        if (!hasCompatibleMetadata(knowledge, requestedCategory)) {
            return false;
        }
        List<Float> embedding = KnowledgeDocumentSupport.parseVector(
                knowledge.getEmbeddingStr()
        );
        if (embedding.size() != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            return false;
        }
        double normSquared = 0.0;
        for (Float value : embedding) {
            if (value == null || !Float.isFinite(value)) {
                return false;
            }
            normSquared += (double) value * value;
        }
        return normSquared > 0.0 && Double.isFinite(normSquared);
    }

    private boolean hasCompatibleMetadata(
            SystemKnowledge knowledge,
            KnowledgeCategory requestedCategory
    ) {
        return knowledge != null
                && knowledge.isActive()
                && !knowledge.isDeleted()
                && (requestedCategory == null || knowledge.getCategory() == requestedCategory)
                && Objects.equals(
                        knowledge.getEmbeddingModel(),
                        embeddingClient.getEmbeddingModel()
                )
                && Objects.equals(
                        knowledge.getEmbeddingDimensions(),
                        SystemKnowledge.EMBEDDING_DIMENSIONS
                )
                && Objects.equals(
                        knowledge.getContentHash(),
                        KnowledgeDocumentSupport.contentHash(knowledge)
                );
    }

    private double lexicalScore(
            String query,
            Set<String> queryTerms,
            SystemKnowledge knowledge
    ) {
        Set<String> titleTerms = terms(knowledge.getTitle());
        Set<String> documentTerms = new HashSet<>(titleTerms);
        documentTerms.addAll(terms(knowledge.getContent()));

        long documentMatches = queryTerms.stream().filter(documentTerms::contains).count();
        long titleMatches = queryTerms.stream().filter(titleTerms::contains).count();
        double coverage = (double) documentMatches / queryTerms.size();
        double titleCoverage = (double) titleMatches / queryTerms.size();

        String normalizedQuery = normalize(query).trim();
        String normalizedDocument = normalize(
                safeText(knowledge.getTitle()) + " " + safeText(knowledge.getContent())
        );
        double phraseBoost = normalizedQuery.length() >= 5 && normalizedDocument.contains(normalizedQuery)
                ? 1.0
                : 0.0;
        return Math.min(1.0, 0.70 * coverage + 0.25 * titleCoverage + 0.05 * phraseBoost);
    }

    private double cosineSimilarity(List<Float> left, List<Float> right) {
        if (left.isEmpty() || left.size() != right.size()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int index = 0; index < left.size(); index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return Math.max(
                0.0,
                Math.min(1.0, dotProduct / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm)))
        );
    }

    private Map<String, Object> toResult(ScoredKnowledge scored) {
        SystemKnowledge knowledge = scored.knowledge();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", safeId(knowledge));
        result.put("source", "system_knowledge");
        result.put("category", categoryLabel(knowledge.getCategory()));
        result.put("title", knowledge.getTitle());
        result.put("content", knowledge.getContent());
        result.put("relevance", roundScore(scored.score()));
        return result;
    }

    private String emptyResult(String query, KnowledgeCategory category, String message)
            throws JsonProcessingException {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        if (category != null) {
            response.put("category", categoryLabel(category));
        }
        response.put("retrievalMode", "lexical");
        response.put("policies", List.of());
        response.put("message", message);
        return objectMapper.writeValueAsString(response);
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"Knowledge retrieval failed\"}";
        }
    }

    private Set<String> terms(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }

        Set<String> result = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        return NON_WORD.matcher(
                        withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT)
                )
                .replaceAll(" ")
                .trim();
    }

    private String stringParam(Map<String, Object> params, String key) {
        if (params == null || !(params.get(key) instanceof String value) || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safeId(SystemKnowledge knowledge) {
        return knowledge.getId() == null ? "" : knowledge.getId().toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private double roundScore(double score) {
        return Math.round(score * 10_000.0) / 10_000.0;
    }

    private record CategorySelection(KnowledgeCategory category, boolean valid) {
    }

    private record ScoredKnowledge(
            SystemKnowledge knowledge,
            double score,
            double lexicalScore,
            double semanticScore
    ) {
    }
}
