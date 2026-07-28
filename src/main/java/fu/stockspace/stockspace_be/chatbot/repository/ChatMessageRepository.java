package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Lấy 10 tin nhắn gần nhất của session — dùng cho context history gửi Gemini */
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.isDeleted = false ORDER BY m.createdAt DESC LIMIT 10")
    List<ChatMessage> findTop10BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    /** Lấy toàn bộ tin nhắn của session — dùng để hiển thị lịch sử */
    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.isDeleted = false ORDER BY m.createdAt ASC")
    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
