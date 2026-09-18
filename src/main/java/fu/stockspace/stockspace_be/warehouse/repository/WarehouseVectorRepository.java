package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** pgvector retrieval for public warehouse profiles. */
@Repository
public class WarehouseVectorRepository {

    private static final int HARD_MAX_CANDIDATES = 200;
    private final EntityManager entityManager;
    private final int efSearch;

    public WarehouseVectorRepository(
            EntityManager entityManager,
            @Value("${app.chatbot.rag.pgvector.ef-search:100}") int efSearch
    ) {
        this.entityManager = entityManager;
        this.efSearch = Math.max(40, Math.min(efSearch, 1_000));
    }

    @Transactional(readOnly = true)
    public List<WarehouseVectorMatch> findNearest(
            float[] queryVector,
            String embeddingModel,
            String keyword,
            String provinceName,
            String districtName,
            RentalPricingType pricingType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minCapacity,
            BigDecimal maxCapacity,
            Boolean isVerified,
            int requestedLimit
    ) {
        if (!isUsableVector(queryVector) || embeddingModel == null || embeddingModel.isBlank()) {
            return List.of();
        }

        int limit = Math.max(1, Math.min(requestedLimit, HARD_MAX_CANDIDATES));
        configureFilteredHnswScan();
        String hql = """
                SELECT w, cosine_distance(w.searchEmbedding, :queryVector)
                FROM Warehouse w
                WHERE w.isActive = true
                  AND w.isDeleted = false
                  AND w.status = fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.AVAILABLE
                  AND w.publishedAt IS NOT NULL
                  AND w.publishedAt <= CURRENT_TIMESTAMP
                  AND w.visibleUntil IS NOT NULL
                  AND w.visibleUntil >= CURRENT_TIMESTAMP
                  AND w.searchEmbedding IS NOT NULL
                  AND w.searchEmbeddingModel = :embeddingModel
                  AND w.searchEmbeddingDimensions = :embeddingDimensions
                  AND (:keyword IS NULL OR LOWER(w.name) LIKE :keyword
                       OR LOWER(w.address) LIKE :keyword
                       OR LOWER(w.provinceName) LIKE :keyword
                       OR LOWER(w.districtName) LIKE :keyword
                       OR LOWER(w.description) LIKE :keyword
                       OR LOWER(w.type.name) LIKE :keyword)
                  AND (:provinceName IS NULL OR LOWER(w.provinceName) LIKE :provinceName)
                  AND (:districtName IS NULL OR LOWER(w.districtName) LIKE :districtName)
                  AND (:pricingType IS NULL OR w.rentalPricingType = :pricingType)
                  AND (:minPrice IS NULL OR (w.rentalPrice IS NOT NULL AND w.rentalPrice >= :minPrice))
                  AND (:maxPrice IS NULL OR (w.rentalPrice IS NOT NULL AND w.rentalPrice <= :maxPrice))
                  AND (:minCapacity IS NULL OR w.capacity >= :minCapacity)
                  AND (:maxCapacity IS NULL OR w.capacity <= :maxCapacity)
                  AND (:isVerified IS NULL OR w.isVerified = :isVerified)
                ORDER BY cosine_distance(w.searchEmbedding, :queryVector) ASC
                """;

        TypedQuery<Object[]> query = entityManager.createQuery(hql, Object[].class)
                .setParameter("queryVector", queryVector)
                .setParameter("embeddingModel", embeddingModel)
                .setParameter("embeddingDimensions", Warehouse.SEARCH_EMBEDDING_DIMENSIONS)
                .setParameter("keyword", keyword)
                .setParameter("provinceName", provinceName)
                .setParameter("districtName", districtName)
                .setParameter("pricingType", pricingType)
                .setParameter("minPrice", minPrice)
                .setParameter("maxPrice", maxPrice)
                .setParameter("minCapacity", minCapacity)
                .setParameter("maxCapacity", maxCapacity)
                .setParameter("isVerified", isVerified)
                .setMaxResults(limit);

        List<WarehouseVectorMatch> matches = new ArrayList<>();
        for (Object[] row : query.getResultList()) {
            if (row == null || row.length < 2 || !(row[0] instanceof Warehouse warehouse)
                    || !(row[1] instanceof Number distanceValue)) {
                continue;
            }
            double distance = distanceValue.doubleValue();
            if (Double.isFinite(distance)) {
                matches.add(new WarehouseVectorMatch(
                        warehouse,
                        Math.max(-1.0, Math.min(1.0, 1.0 - distance))
                ));
            }
        }
        return List.copyOf(matches);
    }

    private void configureFilteredHnswScan() {
        entityManager.createNativeQuery("SET LOCAL hnsw.iterative_scan = strict_order").executeUpdate();
        entityManager.createNativeQuery("SET LOCAL hnsw.ef_search = " + efSearch).executeUpdate();
    }

    private boolean isUsableVector(float[] vector) {
        if (vector == null || vector.length != Warehouse.SEARCH_EMBEDDING_DIMENSIONS) {
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

    public record WarehouseVectorMatch(Warehouse warehouse, double similarity) {
    }
}
