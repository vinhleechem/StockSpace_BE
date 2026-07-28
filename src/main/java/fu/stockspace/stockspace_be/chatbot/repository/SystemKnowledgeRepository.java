package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SystemKnowledgeRepository extends JpaRepository<SystemKnowledge, UUID> {

    @Query("SELECT k FROM SystemKnowledge k WHERE k.isDeleted = false AND (:category IS NULL OR k.category = :category)")
    List<SystemKnowledge> findByCategoryNotDeleted(@Param("category") String category);

    @Query("SELECT k FROM SystemKnowledge k WHERE k.isDeleted = false")
    List<SystemKnowledge> findAllNotDeleted();

    @Query("SELECT COUNT(k) > 0 FROM SystemKnowledge k WHERE k.title = :title AND k.isDeleted = false")
    boolean existsByTitle(@Param("title") String title);
}
