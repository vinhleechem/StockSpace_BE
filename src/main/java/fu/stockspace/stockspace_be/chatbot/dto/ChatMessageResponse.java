package fu.stockspace.stockspace_be.chatbot.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response cho một tin nhắn trong lịch sử chat.
 */
public record ChatMessageResponse(
        UUID id,
        String role,       // "user" hoặc "assistant"
        String content,
        LocalDateTime createdAt
) {}
