package fu.stockspace.stockspace_be.subscription.scheduler;

import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-09T17:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private Clock businessClock;

    @InjectMocks
    private SubscriptionExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(businessClock.instant()).thenReturn(FIXED_INSTANT);
        org.mockito.Mockito.lenient().when(businessClock.getZone()).thenReturn(BUSINESS_ZONE);
    }

    @Test
    void expireSubscriptions_marksOverdueActiveSubscriptionsAsExpired() {
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .status(SubscriptionStatus.ACTIVE)
                .startDate(TODAY.minusDays(31))
                .endDate(TODAY.minusDays(1))
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
