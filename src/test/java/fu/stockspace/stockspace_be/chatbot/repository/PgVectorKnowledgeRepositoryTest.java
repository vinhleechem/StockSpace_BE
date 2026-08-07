package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgVectorKnowledgeRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Object[]> query;

    @Mock
    private Query nativeQuery;

    private PgVectorKnowledgeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PgVectorKnowledgeRepository(entityManager, 100);
    }

    @Test
    void invalidOrZeroVectorsNeverReachTheDatabase() {
        assertTrue(repository.findNearest(null, "model", null, 10).isEmpty());
        assertTrue(repository.findNearest(new float[2], "model", null, 10).isEmpty());
        assertTrue(repository.findNearest(
                new float[SystemKnowledge.EMBEDDING_DIMENSIONS],
                "model",
                null,
                10
        ).isEmpty());
        float[] nonFinite = unitVector();
        nonFinite[1] = Float.NaN;
        assertTrue(repository.findNearest(nonFinite, "model", null, 10).isEmpty());

        verifyNoInteractions(entityManager);
    }

    @Test
    void queryOrdersByIndexEligibleCosineDistanceAndReturnsSimilarity() {
        SystemKnowledge knowledge = SystemKnowledge.builder()
                .id(UUID.randomUUID())
                .category(KnowledgeCategory.POLICY)
                .title("Policy")
                .content("Content")
                .build();
        when(entityManager.createQuery(any(String.class), eq(Object[].class))).thenReturn(query);
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(0);
        when(query.setParameter(any(String.class), any())).thenReturn(query);
        when(query.setMaxResults(200)).thenReturn(query);
        when(query.getResultList()).thenReturn(
                Collections.singletonList(new Object[]{knowledge, 0.125d})
        );

        List<PgVectorKnowledgeRepository.KnowledgeVectorMatch> matches =
                repository.findNearest(
                        unitVector(),
                        "openai/text-embedding-3-small",
                        KnowledgeCategory.POLICY,
                        999
                );

        assertEquals(1, matches.size());
        assertEquals(0.875d, matches.get(0).similarity(), 0.000001d);
        ArgumentCaptor<String> hql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(hql.capture(), eq(Object[].class));
        assertTrue(hql.getValue().contains(
                "ORDER BY cosine_distance(k.embeddingVector, :queryVector) ASC"
        ));
        assertTrue(hql.getValue().contains("k.category = :category"));
        verify(query).setMaxResults(200);
        verify(entityManager).createNativeQuery(
                "SET LOCAL hnsw.iterative_scan = strict_order"
        );
        verify(entityManager).createNativeQuery("SET LOCAL hnsw.ef_search = 100");
    }

    private float[] unitVector() {
        float[] vector = new float[SystemKnowledge.EMBEDDING_DIMENSIONS];
        vector[0] = 1.0f;
        return vector;
    }
}
