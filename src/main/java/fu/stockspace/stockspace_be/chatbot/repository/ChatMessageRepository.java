package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {


    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.session.id = :sessionId
              AND m.isDeleted = false
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findRecentBySession(
            @Param("sessionId") UUID sessionId,
            Pageable pageable
    );





    List<ChatMessage> findTop200BySession_IdAndIsDeletedFalseOrderByCreatedAtDesc(UUID sessionId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.session.id IN :sessionIds")
    int deleteBySessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
