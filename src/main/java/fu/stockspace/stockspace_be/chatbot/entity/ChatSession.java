package fu.stockspace.stockspace_be.chatbot.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho một phiên hội thoại với chatbot.
 *
 * - User đăng nhập: gắn với user_id
 * - GUEST: không có user_id, dùng session_token (UUID random)
 */
@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_user_id",       columnList = "user_id"),
        @Index(name = "idx_chat_sessions_session_token", columnList = "session_token"),
        @Index(name = "idx_chat_sessions_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ChatSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** User đăng nhập — null nếu GUEST */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * SHA-256 hash của token định danh session cho GUEST.
     * Raw bearer token is only returned to the client and is never persisted.
     * Với user đã đăng nhập: null.
     */
    @Column(name = "session_token", unique = true, length = 64)
    private String sessionToken;

    /** Rolling expiry for anonymous sessions. Null for authenticated sessions. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Tiêu đề session — lấy từ 50 ký tự đầu của tin nhắn đầu tiên */
    @Column(name = "title", length = 100)
    private String title;

    /** Prevent silent lost updates when a session is changed concurrently. */
    @Version
    private long version;
}
