package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.client.EmbeddingClient;
import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.chatbot.repository.PgVectorKnowledgeRepository;
import fu.stockspace.stockspace_be.chatbot.repository.PgVectorKnowledgeRepository.KnowledgeVectorMatch;
import fu.stockspace.stockspace_be.chatbot.repository.SystemKnowledgeRepository;
import fu.stockspace.stockspace_be.chatbot.service.KnowledgeDocumentSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchSystemPolicyToolTest {

    @Mock
    private SystemKnowledgeRepository knowledgeRepository;

    @Mock
    private PgVectorKnowledgeRepository vectorKnowledgeRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    private ObjectMapper objectMapper;
    private SearchSystemPolicyTool tool;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new SearchSystemPolicyTool(
                knowledgeRepository,
                vectorKnowledgeRepository,
                embeddingClient,
                objectMapper,
                25,
                3,
                0.15,
                true
        );
    }

    @Test
    void schemaUsesLowercaseJsonSchemaTypesAndLocalizedCategoryLabels() throws Exception {
        Map<String, Object> schema = tool.getParameterSchema();

        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) properties.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> category = (Map<String, Object>) properties.get("category");
        assertEquals("string", query.get("type"));
        assertEquals("string", category.get("type"));
        assertEquals(
                List.of(
                        "Chính sách",
                        "Câu hỏi thường gặp",
                        "Hủy hợp đồng",
                        "Bảo hiểm và đền bù",
                        "Quy trình thuê kho"
                ),
                category.get("enum")
        );
        assertNoRawKnowledgeMetadata(objectMapper.writeValueAsString(schema));
    }

    @Test
    void rejectsUnknownCategoryWithoutBroadeningSearch() throws Exception {
        String json = tool.execute(
                Map.of("query", "hoàn cọc", "category", "anything"),
                null
        );

        JsonNode response = objectMapper.readTree(json);
        assertTrue(response.has("error"));
        assertTrue(response.get("error").asText().contains("Hủy hợp đồng"));
        assertNoRawKnowledgeMetadata(json);
        verifyNoInteractions(knowledgeRepository, vectorKnowledgeRepository, embeddingClient);
    }

    @Test
    void lexicalFallbackRanksRelevantDocumentAndReturnsNumericProvenance() throws Exception {
        String query = "hoàn cọc khi hủy booking";
        SystemKnowledge matching = document(
                KnowledgeCategory.CANCELLATION,
                "kb.cancel",
                "Hủy booking và hoàn cọc",
                "Tenant được hoàn cọc khi hủy booking còn PENDING."
        );
        SystemKnowledge irrelevant = document(
                KnowledgeCategory.FAQ,
                "kb.wallet",
                "Nạp tiền vào ví",
                "Giao dịch được xử lý qua VNPAY."
        );
        when(knowledgeRepository.findSearchCandidates(eq(KnowledgeCategory.CANCELLATION), any(Pageable.class)))
                .thenReturn(List.of(irrelevant, matching));
        when(embeddingClient.getEmbedding(query)).thenReturn(List.of());

        String json = tool.execute(
                Map.of("query", query, "category", " hủy hợp đồng "),
                null
        );

        JsonNode response = objectMapper.readTree(json);
        assertEquals("Hủy hợp đồng", response.get("category").asText());
        assertEquals("lexical", response.get("retrievalMode").asText());
        assertEquals(1, response.get("policies").size());
        JsonNode first = response.get("policies").get(0);
        assertEquals(matching.getId().toString(), first.get("id").asText());
        assertEquals("system_knowledge", first.get("source").asText());
        assertEquals("Hủy hợp đồng", first.get("category").asText());
        assertTrue(first.get("relevance").isNumber());
        assertFalse(json.contains(matching.getSourceId()));
        assertNoRawKnowledgeMetadata(json);
        verify(knowledgeRepository).findSearchCandidates(
                eq(KnowledgeCategory.CANCELLATION),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 25)
        );
    }

    @Test
    void staleModelOrDimensionCannotInfluenceSemanticRanking() throws Exception {
        String query = "hoàn cọc";
        SystemKnowledge lexicalMatch = document(
                KnowledgeCategory.CANCELLATION,
                "kb.cancel",
                "Hoàn cọc",
                "Hoàn khoản đặt cọc khi booking bị từ chối."
        );
        SystemKnowledge staleSemanticMatch = document(
                KnowledgeCategory.CANCELLATION,
                "kb.unrelated",
                "Thông tin chung",
                "Nội dung không liên quan."
        );
        staleSemanticMatch.setEmbeddingVector(unitVector());
        staleSemanticMatch.setEmbeddingModel("old-model");
        staleSemanticMatch.setEmbeddingDimensions(SystemKnowledge.EMBEDDING_DIMENSIONS);
        staleSemanticMatch.setContentHash(KnowledgeDocumentSupport.contentHash(staleSemanticMatch));

        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(staleSemanticMatch, lexicalMatch));
        when(embeddingClient.getEmbedding(query)).thenReturn(unitVectorList());
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        when(embeddingClient.getEmbeddingModel()).thenReturn("current-model");
        when(vectorKnowledgeRepository.findNearest(
                any(float[].class),
                eq("current-model"),
                isNull(),
                eq(25)
        )).thenReturn(List.of(new KnowledgeVectorMatch(staleSemanticMatch, 0.99)));

        JsonNode response = objectMapper.readTree(tool.execute(Map.of("query", query), null));

        assertEquals(1, response.get("policies").size());
        assertEquals(
                lexicalMatch.getId().toString(),
                response.get("policies").get(0).get("id").asText()
        );
        assertEquals("lexical", response.get("retrievalMode").asText());
    }

    @Test
    void meaninglessSingleTokenDoesNotReturnArbitrarySemanticTopHit() throws Exception {
        String query = "xyzabc";
        SystemKnowledge document = document(
                KnowledgeCategory.POLICY,
                "kb.policy",
                "Quy định đặt cọc",
                "Thông tin đặt cọc."
        );
        markIndexed(document, "current-model");

        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(document));
        when(embeddingClient.getEmbedding(query)).thenReturn(unitVectorList());
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        when(embeddingClient.getEmbeddingModel()).thenReturn("current-model");
        when(vectorKnowledgeRepository.findNearest(
                any(float[].class),
                eq("current-model"),
                isNull(),
                eq(25)
        )).thenReturn(List.of(new KnowledgeVectorMatch(document, 0.99)));

        JsonNode response = objectMapper.readTree(tool.execute(Map.of("query", query), null));

        assertTrue(response.get("policies").isEmpty());
        assertEquals("hybrid", response.get("retrievalMode").asText());
        assertEquals("pgvector", response.get("vectorStore").asText());
        assertTrue(response.get("message").asText().contains("không trả kết quả phỏng đoán"));
    }

    @Test
    void pgVectorSemanticHitIsMergedAndRankedAheadOfLexicalCandidates() throws Exception {
        String query = "quy trình hoàn tiền đặt cọc";
        SystemKnowledge lexical = document(
                KnowledgeCategory.POLICY,
                "kb.lexical",
                "Quy trình đặt cọc",
                "Khách hàng thanh toán khoản đặt cọc."
        );
        SystemKnowledge semantic = document(
                KnowledgeCategory.POLICY,
                "kb.semantic",
                "Xử lý giao dịch",
                "Khoản tiền được hoàn lại theo trạng thái booking."
        );
        markIndexed(semantic, "current-model");

        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(lexical));
        when(embeddingClient.getEmbedding(query)).thenReturn(unitVectorList());
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        when(embeddingClient.getEmbeddingModel()).thenReturn("current-model");
        when(vectorKnowledgeRepository.findNearest(
                any(float[].class),
                eq("current-model"),
                isNull(),
                eq(25)
        )).thenReturn(List.of(new KnowledgeVectorMatch(semantic, 0.95)));

        JsonNode response = objectMapper.readTree(tool.execute(Map.of("query", query), null));

        assertEquals("hybrid", response.get("retrievalMode").asText());
        assertEquals("pgvector", response.get("vectorStore").asText());
        assertEquals(
                semantic.getId().toString(),
                response.get("policies").get(0).get("id").asText()
        );
    }

    @Test
    void partialBackfillMergesNativeAndLegacySemanticScores() throws Exception {
        String query = "quy trinh hoan tien dat coc";
        SystemKnowledge nativeDocument = document(
                KnowledgeCategory.POLICY,
                "kb.native",
                "Xu ly giao dich",
                "Thong tin khac ve booking."
        );
        markIndexed(nativeDocument, "current-model");
        SystemKnowledge legacyOnlyDocument = document(
                KnowledgeCategory.POLICY,
                "kb.legacy-only",
                "Hoan tien",
                "Khoan dat coc duoc tra lai."
        );
        markIndexed(legacyOnlyDocument, "current-model");
        legacyOnlyDocument.setEmbeddingVector(null);

        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(legacyOnlyDocument));
        when(embeddingClient.getEmbedding(query)).thenReturn(unitVectorList());
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        when(embeddingClient.getEmbeddingModel()).thenReturn("current-model");
        when(vectorKnowledgeRepository.findNearest(
                any(float[].class),
                eq("current-model"),
                isNull(),
                eq(25)
        )).thenReturn(List.of(new KnowledgeVectorMatch(nativeDocument, 0.85)));

        JsonNode response = objectMapper.readTree(tool.execute(Map.of("query", query), null));

        assertEquals("hybrid", response.get("retrievalMode").asText());
        assertEquals("pgvector+legacy-text", response.get("vectorStore").asText());
        assertEquals(
                legacyOnlyDocument.getId().toString(),
                response.get("policies").get(0).get("id").asText()
        );
        assertTrue(response.toString().contains(nativeDocument.getId().toString()));
    }

    @Test
    void pgVectorFailureFallsBackToLexicalResults() throws Exception {
        String query = "quy định đặt cọc";
        SystemKnowledge lexical = document(
                KnowledgeCategory.POLICY,
                "kb.deposit",
                "Quy định đặt cọc",
                "Khách hàng thanh toán đặt cọc theo booking."
        );
        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(lexical));
        when(embeddingClient.getEmbedding(query)).thenReturn(unitVectorList());
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        when(embeddingClient.getEmbeddingModel()).thenReturn("current-model");
        when(vectorKnowledgeRepository.findNearest(
                any(float[].class),
                eq("current-model"),
                isNull(),
                eq(25)
        )).thenThrow(new IllegalStateException("pgvector unavailable"));

        JsonNode response = objectMapper.readTree(tool.execute(Map.of("query", query), null));

        assertEquals("lexical", response.get("retrievalMode").asText());
        assertEquals(
                lexical.getId().toString(),
                response.get("policies").get(0).get("id").asText()
        );
        assertFalse(response.has("vectorStore"));
    }

    @Test
    void wrongDimensionEmbeddingSkipsPgVectorQuery() throws Exception {
        String query = "quy định đặt cọc";
        SystemKnowledge lexical = document(
                KnowledgeCategory.POLICY,
                "kb.deposit",
                "Quy định đặt cọc",
                "Thông tin đặt cọc."
        );
        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(lexical));
        when(embeddingClient.getEmbedding(query)).thenReturn(List.of(1.0f, 0.0f));

        JsonNode response = objectMapper.readTree(tool.execute(Map.of("query", query), null));

        assertEquals("lexical", response.get("retrievalMode").asText());
        verifyNoInteractions(vectorKnowledgeRepository);
    }

    @Test
    void disabledPgVectorReadPathUsesOneReleaseLegacySemanticFallback() throws Exception {
        String query = "quy trình hoàn tiền đặt cọc";
        SystemKnowledge document = document(
                KnowledgeCategory.POLICY,
                "kb.rollback",
                "Quy trình hoàn tiền đặt cọc",
                "Khoản đặt cọc được hoàn theo trạng thái booking."
        );
        markIndexed(document, "current-model");
        SearchSystemPolicyTool rollbackTool = new SearchSystemPolicyTool(
                knowledgeRepository,
                vectorKnowledgeRepository,
                embeddingClient,
                objectMapper,
                25,
                3,
                0.15,
                false
        );
        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(List.of(document));
        when(embeddingClient.getEmbedding(query)).thenReturn(unitVectorList());
        when(embeddingClient.getEmbeddingDimensions()).thenReturn(SystemKnowledge.EMBEDDING_DIMENSIONS);
        when(embeddingClient.getEmbeddingModel()).thenReturn("current-model");

        JsonNode response = objectMapper.readTree(
                rollbackTool.execute(Map.of("query", query), null)
        );

        assertEquals("hybrid", response.get("retrievalMode").asText());
        assertEquals("legacy-text", response.get("vectorStore").asText());
        assertEquals(
                document.getId().toString(),
                response.get("policies").get(0).get("id").asText()
        );
        verifyNoInteractions(vectorKnowledgeRepository);
    }

    @Test
    void topKIsHardCapped() throws Exception {
        String query = "cọc";
        List<SystemKnowledge> documents = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            documents.add(document(
                    KnowledgeCategory.POLICY,
                    "kb.deposit." + index,
                    "Thông tin cọc " + index,
                    "Quy định cọc cho giao dịch."
            ));
        }
        when(knowledgeRepository.findSearchCandidates(isNull(), any(Pageable.class)))
                .thenReturn(documents);
        when(embeddingClient.getEmbedding(query)).thenReturn(List.of());

        JsonNode response = objectMapper.readTree(
                tool.execute(Map.of("query", query, "topK", 999), null)
        );

        assertEquals(SearchSystemPolicyTool.MAX_TOP_K, response.get("policies").size());
    }

    @Test
    void meaninglessStopWordsAvoidRepositoryAndEmbeddingCalls() throws Exception {
        JsonNode response = objectMapper.readTree(
                tool.execute(Map.of("query", "và là không"), null)
        );

        assertTrue(response.get("policies").isEmpty());
        verify(knowledgeRepository, never()).findSearchCandidates(any(), any());
        verifyNoInteractions(vectorKnowledgeRepository);
        verify(embeddingClient, never()).getEmbedding(any());
    }

    private void markIndexed(SystemKnowledge document, String model) {
        document.setEmbeddingVector(unitVector());
        document.setEmbeddingStr("[1.0" + ",0.0".repeat(SystemKnowledge.EMBEDDING_DIMENSIONS - 1) + "]");
        document.setEmbeddingModel(model);
        document.setEmbeddingDimensions(SystemKnowledge.EMBEDDING_DIMENSIONS);
        document.setContentHash(KnowledgeDocumentSupport.contentHash(document));
    }

    private float[] unitVector() {
        float[] vector = new float[SystemKnowledge.EMBEDDING_DIMENSIONS];
        vector[0] = 1.0f;
        return vector;
    }

    private List<Float> unitVectorList() {
        List<Float> vector = new ArrayList<>(
                java.util.Collections.nCopies(SystemKnowledge.EMBEDDING_DIMENSIONS, 0.0f)
        );
        vector.set(0, 1.0f);
        return List.copyOf(vector);
    }

    private void assertNoRawKnowledgeMetadata(String json) {
        assertFalse(json.contains("\"sourceId\""));
        for (KnowledgeCategory category : KnowledgeCategory.values()) {
            assertFalse(json.contains(category.name()));
        }
    }

    private SystemKnowledge document(
            KnowledgeCategory category,
            String sourceId,
            String title,
            String content
    ) {
        return SystemKnowledge.builder()
                .id(UUID.randomUUID())
                .category(category)
                .sourceId(sourceId)
                .title(title)
                .content(content)
                .isActive(true)
                .isDeleted(false)
                .build();
    }
}
