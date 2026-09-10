package fu.stockspace.stockspace_be.chatbot.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;





record PreparedChatSession(
        UUID sessionId,
        String guestToken,
        List<Map<String, Object>> history,
        ConversationMemory memory
) {

    PreparedChatSession(
            UUID sessionId,
            String guestToken,
            List<Map<String, Object>> history
    ) {
        this(sessionId, guestToken, history, ConversationMemory.empty());
    }

    PreparedChatSession {
        history = history == null ? List.of() : List.copyOf(history);
        memory = memory == null ? ConversationMemory.empty() : memory;
    }
}
