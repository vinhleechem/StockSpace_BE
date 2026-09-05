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
import java.util.Set;








@Slf4j
@Component
public class ChatToolRegistry {

    private static final String GUEST_KEY = "GUEST";

    private static final List<String> PUBLIC_TOOL_NAMES = List.of(
            "searchWarehouses",
            "getWarehouseDetail",
            "getPublicWarehouseLayout",
            "getWarehouseTypes",
            "searchSystemPolicy",
            "getCurrentSystemRules",
            "getServicePackages"
    );
    private static final List<String> GUEST_TOOL_NAMES = merge(
            PUBLIC_TOOL_NAMES,
            List.of("askLoginPrompt")
    );
    private static final List<String> TENANT_TOOL_NAMES = merge(
            PUBLIC_TOOL_NAMES,
            List.of(
                    "getMyContracts",
                    "getContractDetail",
                    "getMyActiveWarehouses",
                    "getWarehouseOwnerContact",
                    "getMyWarehouseLayout",
                    "getMyProductCatalog",
                    "getMyStock",
                    "getInventoryReceipts",
                    "getInventoryAudits",
                    "getStockTransfers",
                    "getWarehouseCapacity",
                    "getMyWallet",
                    "getMyWalletActivity",
                    "getMyNotifications",
                    "getMyActiveSubscription",
                    "previewSubscriptionChange",
                    "suggestPutaway",
                    "suggestOutboundPicking"
            )
    );

    /**
     * Tenant tools that expose operational WMS data or capacity-aware
     * suggestions.  These tools are not exposed to a tenant without a
     * currently active service subscription.  Contract/warehouse metadata,
     * package information, wallet and notification tools remain available so
     * the chatbot can explain what is locked and how to subscribe.
     */
    private static final Set<String> SUBSCRIPTION_REQUIRED_TOOL_NAMES = Set.of(
            "getMyWarehouseLayout",
            "getMyProductCatalog",
            "getMyStock",
            "getInventoryReceipts",
            "getInventoryAudits",
            "getStockTransfers",
            "getWarehouseCapacity",
            "suggestPutaway",
            "suggestOutboundPicking"
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
        this.toolsByRole = Map.copyOf(roleMap);


        log.info("[ChatToolRegistry] Registered {} implemented tools", toolsByName.size());
    }

    public List<ChatTool> getToolsForRole(String roleName) {
        String key = roleName == null
                ? GUEST_KEY
                : roleName.trim().toUpperCase(Locale.ROOT);
        return toolsByRole.getOrDefault(key, List.of());
    }

    /**
     * Applies the subscription gate to a tenant's allowlist before the tool
     * definitions are sent to the model.  This is intentionally separate from
     * role resolution because subscription state is request/user-specific.
     */
    public static List<ChatTool> filterForActiveSubscription(
            List<ChatTool> tools,
            boolean hasActiveSubscription) {
        if (hasActiveSubscription || tools.isEmpty()) {
            return tools;
        }
        return tools.stream()
                .filter(tool -> !requiresActiveSubscription(tool.getName()))
                .toList();
    }

    public static boolean requiresActiveSubscription(String toolName) {
        return toolName != null && SUBSCRIPTION_REQUIRED_TOOL_NAMES.contains(toolName);
    }




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
