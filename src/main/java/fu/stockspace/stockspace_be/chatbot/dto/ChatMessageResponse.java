package fu.stockspace.stockspace_be.chatbot.dto;

import java.time.LocalDateTime;
import java.util.UUID;




public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        LocalDateTime createdAt
) {}
