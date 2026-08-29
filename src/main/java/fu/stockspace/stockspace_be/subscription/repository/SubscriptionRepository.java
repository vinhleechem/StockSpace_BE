package fu.stockspace.stockspace_be.subscription.repository;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
            UUID tenantId, SubscriptionStatus status, LocalDate date);

    List<Subscription> findByStatusAndEndDateBeforeAndIsActiveTrueAndIsDeletedFalse(
            SubscriptionStatus status, LocalDate date);

    Page<Subscription> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("""
            SELECT s FROM Subscription s
            WHERE s.tenant.id = :tenantId
              AND s.status = :status
              AND s.isActive = true
              AND s.isDeleted = false
              AND s.startDate <= :today
              AND s.endDate >= :today
            ORDER BY s.endDate DESC
            """)
    Optional<Subscription> findCurrentByTenantIdAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") SubscriptionStatus status,
            @Param("today") LocalDate today);
}
