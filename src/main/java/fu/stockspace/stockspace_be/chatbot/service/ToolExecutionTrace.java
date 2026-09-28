package fu.stockspace.stockspace_be.chatbot.service;

import java.util.Map;
import java.util.LinkedHashMap;

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
