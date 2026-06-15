package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;

import java.time.LocalDateTime;

/**
 * Entity lưu Refresh Token vào DB.
 *
 * Mỗi user có thể có nhiều refresh token (multi-device login).
 * Khi logout → xóa token tương ứng.
 * Khi logout all devices → xóa hết token của user đó.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Token value — UUID random, unique.
     * Lưu plain text (capstone project).
     * Production nên lưu hash(token) để chống DB leak.
     */
    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    /**
     * User sở hữu token này.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Thời điểm token hết hạn — 7 ngày kể từ lúc tạo.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Kiểm tra token đã hết hạn chưa.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}
