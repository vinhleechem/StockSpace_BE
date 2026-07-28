package fu.stockspace.stockspace_be.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Versioned payloads emitted by the chatbot SSE endpoints.
 *
 * <p>The event name is carried by the SSE frame itself. Payloads deliberately
 * contain no provider, prompt, RAG source or tool implementation details.</p>
 */
public final class ChatStreamEvents {

    public static final int VERSION = 1;

    private ChatStreamEvents() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Session(
            int version,
            UUID requestId,
            UUID sessionId,
            String sessionToken,
            boolean sessionCreated
    ) {
    }

    public record Status(
            UUID requestId,
            String phase,
            String message
    ) {
    }

    public record Delta(
            UUID requestId,
            long sequence,
            String content
    ) {
    }

    public record Ping(
            UUID requestId,
            LocalDateTime timestamp
    ) {
    }

    public record Complete(
            UUID requestId,
            UUID sessionId,
            LocalDateTime timestamp
    ) {
    }

    public record Error(
            UUID requestId,
            String code,
            String message,
            boolean retryable
    ) {
    }
}
