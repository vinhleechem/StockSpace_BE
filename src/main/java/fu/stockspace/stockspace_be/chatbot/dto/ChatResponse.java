package fu.stockspace.stockspace_be.chatbot.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về khi gửi tin nhắn thành công.
 */
public record ChatResponse(
        UUID sessionId,
        String sessionToken,   // Chỉ có giá trị với GUEST session
        String botReply,
        LocalDateTime timestamp
) {}
