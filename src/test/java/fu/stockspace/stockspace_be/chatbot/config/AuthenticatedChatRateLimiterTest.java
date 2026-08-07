package fu.stockspace.stockspace_be.chatbot.config;

import fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedChatRateLimiterTest {

    @Test
    void rejectsRequestAbovePerUserBudgetBeforeRunningAction() {
        AuthenticatedChatRateLimiter limiter = new AuthenticatedChatRateLimiter();
        ReflectionTestUtils.setField(limiter, "requestsPerMinute", 1);
        ReflectionTestUtils.setField(limiter, "maxConcurrentPerUser", 1);
        UUID userId = UUID.randomUUID();
        AtomicInteger actions = new AtomicInteger();

        assertEquals("ok", limiter.execute(userId, () -> {
            actions.incrementAndGet();
            return "ok";
        }));
        assertThrows(
                ChatProviderException.class,
                () -> limiter.execute(userId, () -> {
                    actions.incrementAndGet();
                    return "should-not-run";
                })
        );
        assertEquals(1, actions.get());
    }

    @Test
    void permitKeepsConcurrencySlotUntilClosedAndCloseIsIdempotent() {
        AuthenticatedChatRateLimiter limiter = new AuthenticatedChatRateLimiter();
        ReflectionTestUtils.setField(limiter, "requestsPerMinute", 10);
        ReflectionTestUtils.setField(limiter, "maxConcurrentPerUser", 1);
        UUID userId = UUID.randomUUID();

        AuthenticatedChatRateLimiter.Permit first = limiter.acquire(userId);
        assertThrows(ChatProviderException.class, () -> limiter.acquire(userId));

        first.close();
        first.close();

        AuthenticatedChatRateLimiter.Permit second = limiter.acquire(userId);
        second.close();
    }
}
