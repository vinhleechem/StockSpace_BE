package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity lưu OTP để đặt lại mật khẩu.
 * Mỗi user chỉ có 1 OTP đang hoạt động (xóa cũ trước khi tạo mới).
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** User yêu cầu đặt lại mật khẩu */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Token bảo mật (UUID) */
    @Column(name = "token", nullable = false, length = 100)
    private String token;

    /** Thời điểm OTP hết hạn (15 phút sau khi tạo) */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /** Kiểm tra OTP còn hạn hay không */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
