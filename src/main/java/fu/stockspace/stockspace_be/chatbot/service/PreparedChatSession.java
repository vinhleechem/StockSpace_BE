package fu.stockspace.stockspace_be.chatbot.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detached snapshot prepared in a short database transaction before calling
 * the external AI provider.
 */
record PreparedChatSession(
        UUID sessionId,
        String guestToken,
        List<Map<String, Object>> history
) {
}
