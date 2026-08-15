package fu.stockspace.stockspace_be.staff.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;









@Entity
@Table(
    name = "tenant_members",
    indexes = {
        @Index(name = "idx_tenant_members_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_tenant_members_user_id",   columnList = "user_id")
    },
    uniqueConstraints = {


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


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;


    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;





    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;


    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;


    @Column(name = "resigned_at")
    private LocalDateTime resignedAt;
}

