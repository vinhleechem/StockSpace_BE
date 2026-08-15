package fu.stockspace.stockspace_be.chatbot.dto;

import java.time.LocalDateTime;
import java.util.UUID;




public record ChatSessionResponse(
        UUID id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
