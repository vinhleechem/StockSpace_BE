package fu.stockspace.stockspace_be.staff.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lưu lời mời nhân viên kho gửi qua email.
 * Lời mời hết hạn sau 48 giờ.
 *
 * Luồng:
 *  1. Tenant gọi POST /api/tenant/staffs/invite → tạo bản ghi PENDING + gửi email
 *  2. Staff click link, FE gọi GET /api/auth/staff/invite?token=xxx → validate
 *  3. Staff điền mật khẩu, FE gọi POST /api/auth/staff/accept → tạo User + TenantMember
 */
@Entity
@Table(
    name = "staff_invitations",
    indexes = {
        @Index(name = "idx_staff_invitations_token",     columnList = "token"),
        @Index(name = "idx_staff_invitations_tenant_id", columnList = "tenant_id")
    },
    uniqueConstraints = {
        // Chặn gửi lại lời mời trùng (cùng email + tenant đang PENDING)
        // Kiểm tra ở tầng Service (lọc theo status = PENDING)
        @UniqueConstraint(name = "uq_invitation_token", columnNames = {"token"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Email nhận lời mời */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** Họ tên nhân viên (Tenant nhập khi mời) */
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Số điện thoại (tùy chọn) */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Tenant gửi lời mời */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    /** Token ngẫu nhiên (UUID) đính trong link email */
    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;

    /** Hết hạn sau 48 giờ kể từ khi tạo */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Kiểm tra xem lời mời có còn hiệu lực không */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
