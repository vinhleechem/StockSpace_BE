package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Tool: askLoginPrompt
 * Không query DB — chỉ trả về action yêu cầu frontend redirect đến trang đăng nhập.
 * Dùng khi GUEST hỏi thông tin cần xác thực (hợp đồng, ví, tồn kho...).
 */
@Component
public class AskLoginPromptTool implements ChatTool {

    @Override
    public String getName() { return "askLoginPrompt"; }

    @Override
    public String getDescription() {
        return "Dùng khi user chưa đăng nhập nhưng hỏi về thông tin cá nhân như hợp đồng, " +
               "số dư ví, tồn kho hoặc lịch sử giao dịch. Tool này trả về yêu cầu đăng nhập.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return """
                {
                    "action": "PROMPT_LOGIN",
                    "message": "Vui lòng đăng nhập để xem thông tin này",
                    "loginUrl": "/login"
                }
                """;
    }
}
