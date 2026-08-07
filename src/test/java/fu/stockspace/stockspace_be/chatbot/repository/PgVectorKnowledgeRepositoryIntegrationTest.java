package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.chatbot.service.KnowledgeDocumentSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in database integration test. It must only target an isolated pgvector
 * database because the complete application schema and seed initializer run.
 */
@SpringBootTest(properties = {
        "app.chatbot.rag.indexer.enabled=false",
        "app.data.seed-demo-users=false"
})
@EnabledIfEnvironmentVariable(
        named = "RUN_PGVECTOR_INTEGRATION_TESTS",
        matches = "true"
)
class PgVectorKnowledgeRepositoryIntegrationTest {

    @Autowired
    private SystemKnowledgeRepository knowledgeRepository;

    @Autowired
    private PgVectorKnowledgeRepository vectorKnowledgeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void pgVectorRoundTripUsesCosineOrderingAndProductionIndex() {
        String model = "integration-test-model";
        SystemKnowledge nearest = indexedDocument(
                "it.pgvector.nearest." + UUID.randomUUID(),
                "Nearest",
                "Nearest vector",
                model,
                unitVector(0, 1.0f)
        );
        SystemKnowledge farther = indexedDocument(
                "it.pgvector.farther." + UUID.randomUUID(),
                "Farther",
                "Farther vector",
                model,
                unitVector(1, 1.0f)
        );
        SystemKnowledge wrongCategory = indexedDocument(
                "it.pgvector.category." + UUID.randomUUID(),
                "Wrong category",
                "Must be filtered",
                model,
                unitVector(0, 1.0f)
        );
        wrongCategory.setCategory(KnowledgeCategory.FAQ);
        knowledgeRepository.saveAllAndFlush(List.of(nearest, farther, wrongCategory));

        List<PgVectorKnowledgeRepository.KnowledgeVectorMatch> matches =
                vectorKnowledgeRepository.findNearest(
                        unitVector(0, 1.0f),
                        model,
                        KnowledgeCategory.POLICY,
                        5
                );

        assertEquals(2, matches.size());
        assertEquals(nearest.getId(), matches.get(0).knowledge().getId());
        assertTrue(matches.get(0).similarity() > matches.get(1).similarity());
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM pg_indexes indexes
                            JOIN pg_class index_class
                              ON index_class.relname = indexes.indexname
                            JOIN pg_namespace index_namespace
                              ON index_namespace.oid = index_class.relnamespace
                             AND index_namespace.nspname = indexes.schemaname
                            JOIN pg_index index_state
                              ON index_state.indexrelid = index_class.oid
                            WHERE indexes.schemaname = 'public'
                              AND indexes.indexname = 'idx_system_knowledge_embedding_hnsw'
                              AND indexes.indexdef ILIKE '%USING hnsw%'
                              AND indexes.indexdef ILIKE '%vector_cosine_ops%'
                              AND index_state.indisvalid
                        )
                        """,
                Boolean.class
        )));
    }

    private SystemKnowledge indexedDocument(
            String sourceId,
            String title,
            String content,
            String model,
            float[] vector
    ) {
        SystemKnowledge knowledge = SystemKnowledge.builder()
                .category(KnowledgeCategory.POLICY)
                .sourceId(sourceId)
                .title(title)
                .content(content)
                .embeddingVector(vector)
                .embeddingModel(model)
                .embeddingDimensions(SystemKnowledge.EMBEDDING_DIMENSIONS)
                .isActive(true)
                .isDeleted(false)
                .build();
        knowledge.setContentHash(KnowledgeDocumentSupport.contentHash(knowledge));
        return knowledge;
    }

    private float[] unitVector(int nonZeroIndex, float value) {
        float[] vector = new float[SystemKnowledge.EMBEDDING_DIMENSIONS];
        vector[nonZeroIndex] = value;
        return vector;
    }
}
