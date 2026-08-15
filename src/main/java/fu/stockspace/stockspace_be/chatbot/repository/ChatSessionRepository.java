package fu.stockspace.stockspace_be.chatbot.repository;

import fu.stockspace.stockspace_be.chatbot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {


    @Query("SELECT s FROM ChatSession s WHERE s.user.id = :userId AND s.isDeleted = false ORDER BY s.updatedAt DESC")
    Page<ChatSession> findByUserIdAndNotDeleted(UUID userId, Pageable pageable);


    @Query("SELECT s FROM ChatSession s WHERE s.sessionToken = :token AND s.isDeleted = false")
    Optional<ChatSession> findBySessionTokenAndIsDeletedFalse(String token);


    @Query("SELECT s FROM ChatSession s WHERE s.id = :id AND s.user.id = :userId AND s.isDeleted = false")
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChatSession s WHERE s.id = :id AND s.user.id = :userId AND s.isDeleted = false")
    Optional<ChatSession> findByIdAndUserIdForUpdate(@Param("id") UUID id,
                                                     @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ChatSession s WHERE s.id = :id AND s.sessionToken = :token AND s.isDeleted = false")
    Optional<ChatSession> findGuestByIdAndTokenForUpdate(@Param("id") UUID id,
                                                         @Param("token") String token);

    @Query("""
            SELECT s.id FROM ChatSession s
            WHERE s.user IS NULL
              AND s.expiresAt IS NOT NULL
              AND s.expiresAt < :cutoff
            ORDER BY s.expiresAt ASC
            """)
    List<UUID> findExpiredGuestSessionIds(@Param("cutoff") LocalDateTime cutoff,
                                          Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM ChatSession s
            WHERE s.id IN :ids
              AND s.user IS NULL
              AND s.expiresAt < :cutoff
            """)
    int deleteExpiredGuestSessions(@Param("ids") List<UUID> ids,
                                   @Param("cutoff") LocalDateTime cutoff);
}
