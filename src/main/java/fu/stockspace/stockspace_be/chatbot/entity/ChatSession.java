package fu.stockspace.stockspace_be.chatbot.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;







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


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;






    @Column(name = "session_token", unique = true, length = 64)
    private String sessionToken;


    @Column(name = "expires_at")
    private LocalDateTime expiresAt;


    @Column(name = "title", length = 100)
    private String title;


    @Version
    private long version;
}
