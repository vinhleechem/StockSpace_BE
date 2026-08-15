package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;






@Component
public class AskLoginPromptTool implements ChatTool {

    @Override
    public String getName() { return "askLoginPrompt"; }

    @Override
    public String getDescription() {
        return "Dùng khi người dùng chưa đăng nhập nhưng hỏi về thông tin cá nhân như hợp đồng, " +
               "số dư ví, tồn kho hoặc lịch sử giao dịch. Tool này trả về yêu cầu đăng nhập.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
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
