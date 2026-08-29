package fu.stockspace.stockspace_be.chatbot.config;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ChatProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;




@Component
public class AuthenticatedChatRateLimiter {

    private final ConcurrentHashMap<UUID, UserWindow> users =
            new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Value("${app.chatbot.user-rate-limit.enabled:true}")
    private boolean enabled = true;

    @Value("${app.chatbot.user-rate-limit.requests-per-minute:20}")
    private int requestsPerMinute = 20;

    @Value("${app.chatbot.user-rate-limit.max-concurrent-per-user:2}")
    private int maxConcurrentPerUser = 2;

    @Value("${app.chatbot.user-rate-limit.max-tracked-users:10000}")
    private int maxTrackedUsers = 10_000;

    public <T> T execute(UUID userId, Supplier<T> action) {
        try (Permit ignored = acquire(userId)) {
            return action.get();
        }
    }





    public Permit acquire(UUID userId) {
        if (!enabled) {
            return new Permit(null);
        }
        if (userId == null) {
            throw new ChatProviderException(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
        }

        long now = System.nanoTime();
        cleanupOccasionally(now);
        UserWindow window = users.get(userId);
        if (window == null) {
            if (users.size() >= Math.max(100, maxTrackedUsers)) {
                throw new ChatProviderException(
                        ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
            }
            window = users.computeIfAbsent(userId, ignored -> new UserWindow(now));
        }

        if (!window.tryEnter(
                now,
                Math.max(1, requestsPerMinute),
                Math.max(1, maxConcurrentPerUser)
        )) {
            throw new ChatProviderException(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);
        }
        return new Permit(window);
    }

    private void cleanupOccasionally(long now) {
        if ((requestCounter.incrementAndGet() & 255L) != 0L) {
            return;
        }
        long staleBefore = now - Duration.ofMinutes(10).toNanos();
        users.entrySet().removeIf(entry ->
                entry.getValue().isStale(staleBefore));
    }

    private static final class UserWindow {
        private long windowStartedAt;
        private long lastSeenAt;
        private int requestCount;
        private int inFlight;

        private UserWindow(long now) {
            windowStartedAt = now;
            lastSeenAt = now;
        }

        private synchronized boolean tryEnter(long now,
                                              int requestLimit,
                                              int concurrencyLimit) {
            if (now - windowStartedAt >= Duration.ofMinutes(1).toNanos()) {
                windowStartedAt = now;
                requestCount = 0;
            }
            lastSeenAt = now;
            if (requestCount >= requestLimit || inFlight >= concurrencyLimit) {
                return false;
            }
            requestCount++;
            inFlight++;
            return true;
        }

        private synchronized void leave(long now) {
            inFlight = Math.max(0, inFlight - 1);
            lastSeenAt = now;
        }

        private synchronized boolean isStale(long staleBefore) {
            return inFlight == 0 && lastSeenAt < staleBefore;
        }
    }





    public static final class Permit implements AutoCloseable {

        private final UserWindow window;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(UserWindow window) {
            this.window = window;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true) && window != null) {
                window.leave(System.nanoTime());
            }
        }
    }
}
