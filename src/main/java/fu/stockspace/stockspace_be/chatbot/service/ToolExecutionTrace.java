package fu.stockspace.stockspace_be.chatbot.service;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Internal trace for a tool execution. The trace is never returned by the chat
 * history API; it is reduced to safe entity references before being persisted
 * as conversation memory.
 */
public record ToolExecutionTrace(
        String toolName,
        Map<String, Object> arguments,
        String result,
        boolean successful,
        long durationMs
) {

    public ToolExecutionTrace {
        if (arguments == null || arguments.isEmpty()) {
            arguments = Map.of();
        } else {
            Map<String, Object> safeArguments = new LinkedHashMap<>();
            arguments.forEach((key, value) -> {
                if (key != null && value != null) {
                    safeArguments.put(key, value);
                }
            });
            arguments = Map.copyOf(safeArguments);
        }
        result = result == null ? "" : result;
        durationMs = Math.max(0L, durationMs);
    }
}
