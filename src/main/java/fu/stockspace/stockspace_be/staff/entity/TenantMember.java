package fu.stockspace.stockspace_be.staff.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bảng liên kết Staff ↔ Tenant theo Membership pattern.
 *
 * Một User có ROLE_STAFF chỉ có thể có 1 membership ACTIVE tại 1 thời điểm
 * (ràng buộc: không có 2 bản ghi cùng user_id mà is_deleted = false).
 * Lịch sử membership cũ được giữ lại (is_deleted = true) để đảm bảo
 * tính toàn vẹn dữ liệu với các phiếu nhập/xuất kho cũ.
 */
@Entity
@Table(
    name = "tenant_members",
    indexes = {
        @Index(name = "idx_tenant_members_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_tenant_members_user_id",   columnList = "user_id")
    },
    uniqueConstraints = {
        // Một Staff chỉ được là nhân viên của 1 Tenant tại cùng thời điểm (not deleted)
        // Ràng buộc thực sự được kiểm tra ở tầng Service (vì SQL UNIQUE không filter theo is_deleted)
        @UniqueConstraint(name = "uq_tenant_members_user_tenant", columnNames = {"user_id", "tenant_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Staff — người được mời làm nhân viên */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Tenant — người sở hữu kho và mời Staff */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    /** Trạng thái hoạt động — false khi bị tạm khóa (gói hết hạn / downgrade) */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    /**
     * Soft-delete — true khi Tenant xóa Staff khỏi tổ chức.
     * Bản ghi được giữ lại để đảm bảo tính toàn vẹn lịch sử phiếu nhập/xuất.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    /** Thời điểm Staff chính thức gia nhập (sau khi click link xác nhận) */
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /** Thời điểm Staff chính thức rời khỏi Tenant (sa thải / thôi việc) */
    @Column(name = "resigned_at")
    private LocalDateTime resignedAt;
}

