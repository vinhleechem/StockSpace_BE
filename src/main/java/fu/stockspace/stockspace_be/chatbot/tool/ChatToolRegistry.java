package fu.stockspace.stockspace_be.chatbot.tool;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable role-to-tool registry.
 *
 * <p>The registry advertises only implemented tools. Authorization is enforced
 * again by {@code ChatbotService}, which resolves model calls exclusively from
 * the role-scoped list returned here.</p>
 */
@Slf4j
@Component
public class ChatToolRegistry {

    private static final String GUEST_KEY = "GUEST";

    private static final List<String> PUBLIC_TOOL_NAMES = List.of(
            "searchWarehouses",
            "getWarehouseDetail",
            "searchSystemPolicy"
    );
    private static final List<String> GUEST_TOOL_NAMES = merge(
            PUBLIC_TOOL_NAMES,
            List.of("askLoginPrompt")
    );
    private static final List<String> TENANT_TOOL_NAMES = merge(
            PUBLIC_TOOL_NAMES,
            List.of("getMyContracts", "getContractDetail", "getMyStock", "getMyWallet")
    );

    private final Map<String, List<ChatTool>> toolsByRole;
    private final Map<String, ChatTool> toolsByName;

    public ChatToolRegistry(List<ChatTool> allTools) {
        Map<String, ChatTool> indexed = new LinkedHashMap<>();
        for (ChatTool tool : allTools) {
            ChatTool duplicate = indexed.putIfAbsent(tool.getName(), tool);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "Duplicate chatbot tool name: " + tool.getName());
            }
        }
        this.toolsByName = Map.copyOf(indexed);

        Map<String, List<ChatTool>> roleMap = new HashMap<>();
        roleMap.put(GUEST_KEY, requiredTools(GUEST_TOOL_NAMES));
        roleMap.put(RoleType.ROLE_TENANT.name(), requiredTools(TENANT_TOOL_NAMES));

        // Private tools for these roles belong to Dev B and are not present in
        // this branch. Give them safe public/RAG capabilities instead of
        // advertising nonexistent tools and encouraging hallucinated data.
        List<ChatTool> publicTools = requiredTools(PUBLIC_TOOL_NAMES);
        roleMap.put(RoleType.ROLE_OWNER.name(), publicTools);
        roleMap.put(RoleType.ROLE_STAFF.name(), publicTools);
        roleMap.put(RoleType.ROLE_ADMIN.name(), publicTools);
        roleMap.put(RoleType.ROLE_INSPECTOR.name(), publicTools);
        this.toolsByRole = Map.copyOf(roleMap);

        log.info("[ChatToolRegistry] Registered {} implemented tools", toolsByName.size());
    }

    public List<ChatTool> getToolsForRole(String roleName) {
        String key = roleName == null
                ? GUEST_KEY
                : roleName.trim().toUpperCase(Locale.ROOT);
        return toolsByRole.getOrDefault(key, toolsByRole.get(GUEST_KEY));
    }

    /**
     * Metadata lookup only. Do not use this method as an authorization check.
     */
    public Optional<ChatTool> findByName(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    private List<ChatTool> requiredTools(List<String> names) {
        List<ChatTool> result = new ArrayList<>(names.size());
        for (String name : names) {
            ChatTool tool = toolsByName.get(name);
            if (tool == null) {
                throw new IllegalStateException(
                        "Required chatbot tool is not implemented: " + name);
            }
            result.add(tool);
        }
        return List.copyOf(result);
    }

    private static <T> List<T> merge(List<T> first, List<T> second) {
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }
}
