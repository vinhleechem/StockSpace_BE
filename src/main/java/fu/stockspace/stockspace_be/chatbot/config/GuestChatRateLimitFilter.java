package fu.stockspace.stockspace_be.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;





@Component
@RequiredArgsConstructor
public class GuestChatRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> GUEST_AI_PATHS = Set.of(
            "/api/chat/guest/send",
            "/api/chat/guest/stream"
    );
    private static final String OVERFLOW_KEY = "_overflow_";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, ClientWindow> clients =
            new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Value("${app.chatbot.guest-rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.chatbot.guest-rate-limit.requests-per-minute:8}")
    private int requestsPerMinute;

    @Value("${app.chatbot.guest-rate-limit.max-concurrent-per-client:2}")
    private int maxConcurrentPerClient;

    @Value("${app.chatbot.guest-rate-limit.max-tracked-clients:10000}")
    private int maxTrackedClients;

    @Value("${app.chatbot.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !GUEST_AI_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        long now = System.nanoTime();
        cleanupOccasionally(now);

        String clientKey = resolveClientKey(request);
        if (!clients.containsKey(clientKey)
                && clients.size() >= Math.max(100, maxTrackedClients)) {
            clientKey = OVERFLOW_KEY;
        }

        ClientWindow window = clients.computeIfAbsent(
                clientKey,
                ignored -> new ClientWindow(now)
        );
        if (!window.tryEnter(
                now,
                Math.max(1, requestsPerMinute),
                Math.max(1, maxConcurrentPerClient)
        )) {
            writeRateLimited(response);
            return;
        }

        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                window.leave(System.nanoTime());
            }
        };

        try {
            filterChain.doFilter(request, response);
        } finally {
            releaseWhenRequestTerminates(request, release);
        }
    }






    private void releaseWhenRequestTerminates(HttpServletRequest request,
                                              Runnable release) {
        if (!request.isAsyncStarted()) {
            release.run();
            return;
        }

        AsyncListener listener = new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
                release.run();
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                release.run();
            }

            @Override
            public void onError(AsyncEvent event) {
                release.run();
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                try {
                    event.getAsyncContext().addListener(this);
                } catch (IllegalStateException exception) {
                    release.run();
                }
            }
        };

        try {
            request.getAsyncContext().addListener(listener);
        } catch (IllegalStateException exception) {


            release.run();
        }
    }

    private String resolveClientKey(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return sanitizeKey(realIp);
            }
        }
        return sanitizeKey(request.getRemoteAddr());
    }

    private String sanitizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.trim().replaceAll("[^0-9A-Fa-f:._-]", "?");
        return sanitized.length() <= 64 ? sanitized : sanitized.substring(0, 64);
    }

    private void writeRateLimited(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED.getStatus().value());
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED.getMessage())
        );
    }

    private void cleanupOccasionally(long now) {
        if ((requestCounter.incrementAndGet() & 255L) != 0L) {
            return;
        }
        long staleBefore = now - Duration.ofMinutes(10).toNanos();
        clients.entrySet().removeIf(entry ->
                !OVERFLOW_KEY.equals(entry.getKey())
                        && entry.getValue().isStale(staleBefore));
    }

    private static final class ClientWindow {
        private long windowStartedAt;
        private long lastSeenAt;
        private int requestCount;
        private int inFlight;

        private ClientWindow(long now) {
            this.windowStartedAt = now;
            this.lastSeenAt = now;
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
}
