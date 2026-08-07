package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Lấy 10 tin nhắn gần nhất của session làm context cho model. */
    List<ChatMessage> findTop10BySession_IdAndIsDeletedFalseOrderByCreatedAtDesc(UUID sessionId);

    /**
     * API legacy trả List nhưng được chặn ở 200 tin gần nhất để tránh tải toàn
     * bộ một session không giới hạn vào bộ nhớ.
     */
    List<ChatMessage> findTop200BySession_IdAndIsDeletedFalseOrderByCreatedAtDesc(UUID sessionId);

    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.session.id IN :sessionIds")
    int deleteBySessionIds(@Param("sessionIds") List<UUID> sessionIds);
}
