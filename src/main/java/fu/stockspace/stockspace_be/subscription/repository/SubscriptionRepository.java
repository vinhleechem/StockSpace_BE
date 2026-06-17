package fu.stockspace.stockspace_be.subscription.repository;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
            Long tenantId, SubscriptionStatus status, LocalDate date);
    Page<Subscription> findByTenantId(Long tenantId, Pageable pageable);
}