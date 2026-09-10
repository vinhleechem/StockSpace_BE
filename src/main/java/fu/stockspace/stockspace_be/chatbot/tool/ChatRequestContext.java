package fu.stockspace.stockspace_be.chatbot.tool;

import java.util.UUID;

/**
 * Request-scoped facts supplied by the authenticated application, never by the
 * language model. The active warehouse comes from the screen currently open in
 * the client and must still be authorized by every tool that uses it.
 */
public record ChatRequestContext(
        UUID userId,
        UUID activeWarehouseId,
        String activeWarehouseName,
        String activeScreen
) {

    public ChatRequestContext(UUID userId, UUID activeWarehouseId) {
        this(userId, activeWarehouseId, null, null);
    }

    public ChatRequestContext(UUID userId, UUID activeWarehouseId, String activeWarehouseName) {
        this(userId, activeWarehouseId, activeWarehouseName, null);
    }

    public static ChatRequestContext guest() {
        return new ChatRequestContext(null, null, null, null);
    }
}
