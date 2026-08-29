package fu.stockspace.stockspace_be.staff.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;










@Entity
@Table(
    name = "staff_invitations",
    indexes = {
        @Index(name = "idx_staff_invitations_token",     columnList = "token"),
        @Index(name = "idx_staff_invitations_tenant_id", columnList = "tenant_id")
    },
    uniqueConstraints = {


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


    @Column(name = "email", nullable = false, length = 255)
    private String email;


    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;


    @Column(name = "phone", length = 20)
    private String phone;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;


    @Column(name = "token", nullable = false, unique = true, length = 255)
    private String token;


    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvitationStatus status = InvitationStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
