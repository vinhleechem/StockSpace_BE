package fu.stockspace.stockspace_be.subscription.scheduler;

import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 5 0 * * ?")
    @Transactional
    public void expireSubscriptions() {
        LocalDate today = LocalDate.now();
        List<Subscription> expiredSubscriptions = subscriptionRepository
                .findByStatusAndEndDateBeforeAndIsActiveTrueAndIsDeletedFalse(
                        SubscriptionStatus.ACTIVE, today);

        if (expiredSubscriptions.isEmpty()) {
            return;
        }

        expiredSubscriptions.forEach(subscription -> {
            subscription.setStatus(SubscriptionStatus.EXPIRED);
            subscription.setActive(false);
        });
        subscriptionRepository.saveAll(expiredSubscriptions);

        log.info("Marked {} subscription(s) as expired", expiredSubscriptions.size());
    }
}
