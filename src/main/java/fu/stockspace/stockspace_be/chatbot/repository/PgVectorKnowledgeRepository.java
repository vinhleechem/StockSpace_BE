package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes bounded nearest-neighbour retrieval inside PostgreSQL/pgvector.
 *
 * <p>Hibernate Vector renders {@code cosine_distance()} as pgvector's
 * index-eligible {@code <=>} operator. The direct distance ordering and hard
 * limit are intentional so PostgreSQL can use the partial HNSW index.</p>
 */
@Repository
public class PgVectorKnowledgeRepository {

    static final int HARD_MAX_CANDIDATES = 200;
    static final int MIN_EF_SEARCH = 40;
    static final int MAX_EF_SEARCH = 1_000;

    private final EntityManager entityManager;
    private final int efSearch;

    public PgVectorKnowledgeRepository(
            EntityManager entityManager,
            @Value("${app.chatbot.rag.pgvector.ef-search:100}") int efSearch
    ) {
        this.entityManager = entityManager;
        this.efSearch = Math.max(MIN_EF_SEARCH, Math.min(efSearch, MAX_EF_SEARCH));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeVectorMatch> findNearest(
            float[] queryVector,
            String embeddingModel,
            KnowledgeCategory category,
            int requestedLimit
    ) {
        if (!isUsableVector(queryVector)
                || embeddingModel == null
                || embeddingModel.isBlank()) {
            return List.of();
        }

        int limit = Math.max(1, Math.min(requestedLimit, HARD_MAX_CANDIDATES));
        configureFilteredHnswScan();
        String categoryPredicate = category == null ? "" : " AND k.category = :category";
        String hql = """
                SELECT k, cosine_distance(k.embeddingVector, :queryVector)
                FROM SystemKnowledge k
                WHERE k.isActive = true
                  AND k.isDeleted = false
                  AND k.embeddingVector IS NOT NULL
                  AND k.embeddingModel = :embeddingModel
                  AND k.embeddingDimensions = :embeddingDimensions
                """ + categoryPredicate + """

                ORDER BY cosine_distance(k.embeddingVector, :queryVector) ASC
                """;

        TypedQuery<Object[]> query = entityManager.createQuery(hql, Object[].class)
                .setParameter("queryVector", queryVector)
                .setParameter("embeddingModel", embeddingModel)
                .setParameter("embeddingDimensions", SystemKnowledge.EMBEDDING_DIMENSIONS)
                .setMaxResults(limit);
        if (category != null) {
            query.setParameter("category", category);
        }

        List<KnowledgeVectorMatch> matches = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            if (row == null
                    || row.length < 2
                    || !(row[0] instanceof SystemKnowledge knowledge)
                    || !(row[1] instanceof Number distanceValue)) {
                continue;
            }
            double distance = distanceValue.doubleValue();
            if (!Double.isFinite(distance)) {
                continue;
            }
            double similarity = Math.max(-1.0, Math.min(1.0, 1.0 - distance));
            matches.add(new KnowledgeVectorMatch(knowledge, similarity));
        }
        return List.copyOf(matches);
    }

    /**
     * pgvector applies ordinary WHERE filters after an approximate HNSW scan.
     * Iterative scanning keeps expanding that scan until enough filtered rows
     * are found, while strict_order preserves distance ordering.
     */
    private void configureFilteredHnswScan() {
        Query iterativeScan = entityManager.createNativeQuery(
                "SET LOCAL hnsw.iterative_scan = strict_order"
        );
        iterativeScan.executeUpdate();
        Query searchBreadth = entityManager.createNativeQuery(
                "SET LOCAL hnsw.ef_search = " + efSearch
        );
        searchBreadth.executeUpdate();
    }

    private boolean isUsableVector(float[] vector) {
        if (vector == null || vector.length != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            return false;
        }
        double normSquared = 0.0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
            normSquared += (double) value * value;
        }
        return normSquared > 0.0 && Double.isFinite(normSquared);
    }

    public record KnowledgeVectorMatch(
            SystemKnowledge knowledge,
            double similarity
    ) {
    }
}
