package fu.stockspace.stockspace_be.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;




public record SendMessageRequest(

        @Size(max = 36, message = "sessionId không hợp lệ")
        String sessionId,


        @NotBlank(message = "Nội dung tin nhắn không được để trống")
        @Size(max = 2000, message = "Tin nhắn không được vượt quá 2000 ký tự")
        String message,

        @Schema(description = "Ngữ cảnh kho đang mở. Frontend tự gửi từ màn hình hiện tại; người dùng không cần nhập giá trị này.")
        UUID activeWarehouseId,

        @Size(max = 32, message = "Màn hình hiện tại không hợp lệ")
        @Schema(description = "Màn hình nghiệp vụ đang mở, dùng để hiểu câu hỏi nối tiếp; backend chỉ nhận giá trị trong allowlist.")
        String activeScreen
) {
    /**
     * Keeps existing Java callers source-compatible. HTTP clients may omit the
     * optional active warehouse context as well.
     */
    public SendMessageRequest(String sessionId, String message) {
        this(sessionId, message, null, null);
    }

    public SendMessageRequest(String sessionId, String message, UUID activeWarehouseId) {
        this(sessionId, message, activeWarehouseId, null);
    }
}
