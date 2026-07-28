package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /** Lấy session của user (đã đăng nhập), phân trang, sắp xếp mới nhất trước */
    @Query("SELECT s FROM ChatSession s WHERE s.user.id = :userId AND s.isDeleted = false ORDER BY s.updatedAt DESC")
    Page<ChatSession> findByUserIdAndNotDeleted(UUID userId, Pageable pageable);

    /** Tìm session theo sessionToken (GUEST) */
    @Query("SELECT s FROM ChatSession s WHERE s.sessionToken = :token AND s.isDeleted = false")
    Optional<ChatSession> findBySessionTokenAndIsDeletedFalse(String token);

    /** Tìm session theo id + userId (kiểm tra quyền) */
    @Query("SELECT s FROM ChatSession s WHERE s.id = :id AND s.user.id = :userId AND s.isDeleted = false")
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);
}
