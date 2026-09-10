package fu.stockspace.stockspace_be.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Converts successful tool results into compact, tenant/session-scoped memory. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    static final int MAX_ENTITIES = 24;
    static final int MAX_RECENT_TOOLS = 8;

    private static final Set<String> NAME_FIELDS = Set.of(
            "name", "warehouseName", "productName", "packageName", "title"
    );

    private final ObjectMapper objectMapper;

    public ConversationMemory read(String json) {
        if (json == null || json.isBlank()) {
            return ConversationMemory.empty();
        }
        try {
            ConversationMemory memory = objectMapper.readValue(json, ConversationMemory.class);
            return memory == null ? ConversationMemory.empty() : sanitize(memory);
        } catch (Exception exception) {
            log.warn("[ConversationMemory] Ignoring invalid stored memory cause={}",
                    exception.getClass().getSimpleName());
            return ConversationMemory.empty();
        }
    }

    public String write(ConversationMemory memory) {
        try {
            return objectMapper.writeValueAsString(sanitize(memory));
        } catch (Exception exception) {
            log.warn("[ConversationMemory] Could not serialize memory cause={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    public ConversationMemory merge(
            ConversationMemory current,
            List<ToolExecutionTrace> traces
    ) {
        List<ConversationMemory.EntityReference> discovered = new ArrayList<>();
        LinkedHashSet<String> tools = new LinkedHashSet<>();
        if (traces != null) {
            for (ToolExecutionTrace trace : traces) {
                if (trace == null || !trace.successful()) {
                    continue;
                }
                tools.add(trace.toolName());
                discovered.addAll(extractEntities(trace));
            }
        }
        return ConversationMemory.merged(
                current,
                discovered,
                List.copyOf(tools),
                MAX_ENTITIES,
                MAX_RECENT_TOOLS
        );
    }

    private ConversationMemory sanitize(ConversationMemory memory) {
        if (memory == null) {
            return ConversationMemory.empty();
        }
        return ConversationMemory.merged(
                ConversationMemory.empty(),
                memory.entities(),
                memory.recentTools(),
                MAX_ENTITIES,
                MAX_RECENT_TOOLS
        );
    }

    private List<ConversationMemory.EntityReference> extractEntities(
            ToolExecutionTrace trace
    ) {
        if (trace.result() == null || trace.result().isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(trace.result());
            if (root == null || root.has("error")) {
                return List.of();
            }
            List<ConversationMemory.EntityReference> entities = new ArrayList<>();
            walk(root, trace.toolName(), inferType(trace.toolName()), entities);
            return List.copyOf(entities);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private void walk(
            JsonNode node,
            String toolName,
            String inheritedType,
            List<ConversationMemory.EntityReference> entities
    ) {
        if (node == null || entities.size() >= MAX_ENTITIES) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, toolName, inheritedType, entities);
                if (entities.size() >= MAX_ENTITIES) {
                    break;
                }
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String id = firstUuid(node, "warehouseId", "id", "contractId", "productId", "packageId");
        String name = firstText(node, NAME_FIELDS);
        String type = node.hasNonNull("warehouseId") || node.hasNonNull("warehouseName")
                ? "warehouse"
                : node.hasNonNull("productId") || node.hasNonNull("productName")
                || node.hasNonNull("sku")
                ? "product"
                : node.hasNonNull("contractId")
                ? "contract"
                : inheritedType;
        if (id != null && name != null) {
            ConversationMemory.EntityReference entity =
                    new ConversationMemory.EntityReference(type, id, name, toolName);
            if (entity.isUsable()) {
                entities.add(entity);
            }
        }

        node.fields().forEachRemaining(entry -> {
            if (entities.size() < MAX_ENTITIES) {
                walk(entry.getValue(), toolName, inferType(entry.getKey(), inheritedType), entities);
            }
        });
    }

    private String firstUuid(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || !value.isTextual()) {
                continue;
            }
            try {
                return UUID.fromString(value.asText().trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Try the next known ID field.
            }
        }
        return null;
    }

    private String firstText(JsonNode node, Set<String> fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private String inferType(String toolName) {
        String normalized = toolName == null ? "" : toolName.toLowerCase(Locale.ROOT);
        if (normalized.contains("warehouse") || normalized.contains("stock")
                || normalized.contains("receipt") || normalized.contains("audit")
                || normalized.contains("putaway") || normalized.contains("picking")) {
            return "warehouse";
        }
        if (normalized.contains("contract")) {
            return "contract";
        }
        if (normalized.contains("product") || normalized.contains("sku")) {
            return "product";
        }
        if (normalized.contains("package") || normalized.contains("subscription")) {
            return "service_package";
        }
        return "entity";
    }

    private String inferType(String fieldName, String fallback) {
        String normalized = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        if (normalized.contains("warehouse")) {
            return "warehouse";
        }
        if (normalized.contains("contract")) {
            return "contract";
        }
        if (normalized.contains("product") || normalized.contains("sku")) {
            return "product";
        }
        if (normalized.contains("package") || normalized.contains("subscription")) {
            return "service_package";
        }
        return fallback;
    }
}
