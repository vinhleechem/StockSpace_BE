package fu.stockspace.stockspace_be.chatbot.tool.impl;

import java.util.Map;
import java.util.UUID;

final class ChatToolParameters {

    private ChatToolParameters() {
    }

    static int page(Map<String, Object> params) {
        return boundedInteger(params, "page", 0, 0, 100_000);
    }

    static int pageSize(Map<String, Object> params, int defaultSize, int maxSize) {
        return boundedInteger(params, "pageSize", defaultSize, 1, maxSize);
    }

    static UUID optionalUuid(Map<String, Object> params, String key) {
        Object raw = params == null ? null : params.get(key);
        return raw == null || raw.toString().isBlank()
                ? null
                : UUID.fromString(raw.toString().trim());
    }

    private static int boundedInteger(Map<String, Object> params, String key,
                                      int defaultValue, int minimum, int maximum) {
        Object raw = params == null ? null : params.get(key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw.toString());
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(key + " is outside the allowed range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
