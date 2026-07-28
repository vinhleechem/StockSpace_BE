package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SystemKnowledgeRepository extends JpaRepository<SystemKnowledge, UUID> {

    @Query("""
            SELECT k FROM SystemKnowledge k
            WHERE k.isActive = true
              AND k.isDeleted = false
              AND (:category IS NULL OR k.category = :category)
            ORDER BY k.updatedAt DESC, k.id ASC
            """)
    List<SystemKnowledge> findSearchCandidates(
            @Param("category") KnowledgeCategory category,
            Pageable pageable
    );

    @Query("""
            SELECT k FROM SystemKnowledge k
            WHERE k.isActive = true
              AND k.isDeleted = false
              AND (
                    k.embeddingVector IS NULL
                    OR k.embeddingStr IS NULL
                    OR TRIM(k.embeddingStr) = ''
                    OR k.embeddingModel IS NULL
                    OR k.embeddingModel <> :model
                    OR k.embeddingDimensions IS NULL
                    OR k.embeddingDimensions <> :dimensions
                    OR k.contentHash IS NULL
                    OR TRIM(k.contentHash) = ''
              )
            ORDER BY k.updatedAt ASC, k.id ASC
            """)
    List<SystemKnowledge> findStaleIndexCandidates(
            @Param("model") String model,
            @Param("dimensions") int dimensions,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT k FROM SystemKnowledge k
            WHERE k.id IN :ids
            ORDER BY k.id ASC
            """)
    List<SystemKnowledge> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    Optional<SystemKnowledge> findBySourceId(String sourceId);

    Optional<SystemKnowledge> findFirstByTitleIgnoreCaseAndIsDeletedFalse(String title);
}
