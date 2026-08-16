package fu.stockspace.stockspace_be.subscription.scheduler;

import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionExpiryScheduler scheduler;

    @Test
    void expireSubscriptions_marksOverdueActiveSubscriptionsAsExpired() {
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(31))
                .endDate(LocalDate.now().minusDays(1))
                .isActive(true)
                .isDeleted(false)
                .build();

        when(subscriptionRepository.findByStatusAndEndDateBeforeAndIsActiveTrueAndIsDeletedFalse(
                eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(subscription));

        scheduler.expireSubscriptions();

        assertEquals(SubscriptionStatus.EXPIRED, subscription.getStatus());
        assertFalse(subscription.isActive());
        verify(subscriptionRepository).saveAll(List.of(subscription));
    }
}
