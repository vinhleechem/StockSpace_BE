package fu.stockspace.stockspace_be.chatbot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuestChatRateLimitFilterTest {

    private GuestChatRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GuestChatRateLimitFilter(new ObjectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 1);
        ReflectionTestUtils.setField(filter, "maxConcurrentPerClient", 1);
        ReflectionTestUtils.setField(filter, "maxTrackedClients", 100);
        ReflectionTestUtils.setField(filter, "trustProxyHeaders", false);
    }

    @Test
    void rejectsSecondGuestAiRequestWithoutInvokingApplicationChain() throws Exception {
        MockHttpServletRequest firstRequest =
                guestRequest("203.0.113.10", "/api/chat/guest/send");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(firstRequest, firstResponse, firstChain);

        MockHttpServletRequest secondRequest =
                guestRequest("203.0.113.10", "/api/chat/guest/send");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        MockFilterChain secondChain = new MockFilterChain();
        filter.doFilter(secondRequest, secondResponse, secondChain);

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, secondResponse.getStatus());
        assertEquals("60", secondResponse.getHeader("Retry-After"));
        assertTrue(secondResponse.getContentAsString().contains("\"success\":false"));
        assertNotNull(firstChain.getRequest());
        assertNull(secondChain.getRequest());
    }

    @Test
    void filtersBothBufferedAndStreamingGuestAiEndpoints() {
        assertFalse(filter.shouldNotFilter(
                guestRequest("203.0.113.11", "/api/chat/guest/send")
        ));
        assertFalse(filter.shouldNotFilter(
                guestRequest("203.0.113.11", "/api/chat/guest/stream")
        ));
        assertTrue(filter.shouldNotFilter(
                guestRequest("203.0.113.11", "/api/chat/guest/history")
        ));
    }

    @Test
    void holdsConcurrencySlotUntilAsyncStreamCompletes() throws Exception {
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 10);

        MockHttpServletRequest streamingRequest =
                guestRequest("203.0.113.12", "/api/chat/guest/stream");
        streamingRequest.setAsyncSupported(true);
        MockHttpServletResponse streamingResponse =
                new MockHttpServletResponse();
        FilterChain asyncChain = (request, response) ->
                request.startAsync(request, response);

        filter.doFilter(streamingRequest, streamingResponse, asyncChain);

        MockHttpServletRequest concurrentRequest =
                guestRequest("203.0.113.12", "/api/chat/guest/stream");
        MockHttpServletResponse concurrentResponse =
                new MockHttpServletResponse();
        MockFilterChain concurrentChain = new MockFilterChain();
        filter.doFilter(
                concurrentRequest,
                concurrentResponse,
                concurrentChain
        );

        assertEquals(429, concurrentResponse.getStatus());
        assertNull(concurrentChain.getRequest());

        ((MockAsyncContext) streamingRequest.getAsyncContext()).complete();

        MockHttpServletRequest afterCompletionRequest =
                guestRequest("203.0.113.12", "/api/chat/guest/stream");
        MockHttpServletResponse afterCompletionResponse =
                new MockHttpServletResponse();
        MockFilterChain afterCompletionChain = new MockFilterChain();
        filter.doFilter(
                afterCompletionRequest,
                afterCompletionResponse,
                afterCompletionChain
        );

        assertEquals(200, afterCompletionResponse.getStatus());
        assertNotNull(afterCompletionChain.getRequest());
    }

    @Test
    void releasesAsyncSlotExactlyOnceAcrossTerminalCallbacks()
            throws Exception {
        ReflectionTestUtils.setField(filter, "requestsPerMinute", 10);

        MockHttpServletRequest firstRequest =
                guestRequest("203.0.113.13", "/api/chat/guest/stream");
        firstRequest.setAsyncSupported(true);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        FilterChain asyncChain = (request, response) ->
                request.startAsync(request, response);
        filter.doFilter(firstRequest, firstResponse, asyncChain);

        MockAsyncContext firstContext =
                (MockAsyncContext) firstRequest.getAsyncContext();
        AsyncEvent firstEvent =
                new AsyncEvent(firstContext, firstRequest, firstResponse);
        firstContext.getListeners().get(0).onTimeout(firstEvent);

        MockHttpServletRequest secondRequest =
                guestRequest("203.0.113.13", "/api/chat/guest/stream");
        secondRequest.setAsyncSupported(true);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, asyncChain);

        // Containers may notify both timeout/error and completion. The stale
        // callback from request one must not release request two's slot.
        firstContext.complete();

        MockHttpServletRequest concurrentRequest =
                guestRequest("203.0.113.13", "/api/chat/guest/stream");
        MockHttpServletResponse concurrentResponse =
                new MockHttpServletResponse();
        MockFilterChain concurrentChain = new MockFilterChain();
        filter.doFilter(
                concurrentRequest,
                concurrentResponse,
                concurrentChain
        );

        assertEquals(429, concurrentResponse.getStatus());
        assertNull(concurrentChain.getRequest());
        ((MockAsyncContext) secondRequest.getAsyncContext()).complete();
    }

    private MockHttpServletRequest guestRequest(String remoteAddress,
                                                String requestPath) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", requestPath);
        request.setRemoteAddr(remoteAddress);
        request.setContentType("application/json");
        request.setContent("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return request;
    }
}
