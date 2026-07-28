package fu.stockspace.stockspace_be.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request gửi tin nhắn tới chatbot.
 */
public record SendMessageRequest(
        /** ID session (null nếu tạo session mới) */
        @Size(max = 36, message = "sessionId không hợp lệ")
        String sessionId,

        /** Nội dung tin nhắn */
        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 2000, message = "Tin nhắn không được vượt quá 2000 ký tự")
        String message
) {}
