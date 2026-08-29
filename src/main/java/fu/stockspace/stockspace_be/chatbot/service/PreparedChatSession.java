package fu.stockspace.stockspace_be.chatbot.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;





record PreparedChatSession(
        UUID sessionId,
        String guestToken,
        List<Map<String, Object>> history
) {
}
