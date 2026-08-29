package fu.stockspace.stockspace_be.chatbot.dto;

import java.time.LocalDateTime;
import java.util.UUID;




public record ChatResponse(
        UUID sessionId,
        String sessionToken,
        String botReply,
        LocalDateTime timestamp
) {}
