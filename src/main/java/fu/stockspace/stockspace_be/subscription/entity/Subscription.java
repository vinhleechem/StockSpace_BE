package fu.stockspace.stockspace_be.subscription.entity;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;
import java.util.UUID;



@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscriptions_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_subscriptions_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Subscription extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage servicePackage;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;



    @Column(name = "snapshot_max_staff")
    private Integer snapshotMaxStaff;


    @Column(name = "snapshot_price", precision = 15, scale = 2)
    private java.math.BigDecimal snapshotPrice;


    @Column(name = "snapshot_features", columnDefinition = "TEXT")
    private String snapshotFeatures;


    @Column(name = "snapshot_package_name", length = 150)
    private String snapshotPackageName;
}