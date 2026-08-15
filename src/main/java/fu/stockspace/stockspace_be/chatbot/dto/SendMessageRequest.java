package fu.stockspace.stockspace_be.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;




public record SendMessageRequest(

        @Size(max = 36, message = "sessionId không hợp lệ")
        String sessionId,


        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 2000, message = "Tin nhắn không được vượt quá 2000 ký tự")
        String message
) {}
