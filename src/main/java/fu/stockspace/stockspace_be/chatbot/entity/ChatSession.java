package fu.stockspace.stockspace_be.chatbot.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
        @Index(name = "idx_chat_sessions_session_token", columnList = "session_token")
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
     * Token định danh session cho GUEST (không cần đăng nhập).
     * Với user đã đăng nhập: null.
     */
    @Column(name = "session_token", unique = true, length = 64)
    private String sessionToken;

    /** Tiêu đề session — lấy từ 50 ký tự đầu của tin nhắn đầu tiên */
    @Column(name = "title", length = 100)
    private String title;
}
