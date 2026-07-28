package fu.stockspace.stockspace_be.chatbot.tool;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry quản lý tất cả ChatTools, phân loại theo role.
 *
 * ⚠️ Dev B đăng ký tools mới bằng cách annotate @Component trên class tool.
 * Registry tự inject tất cả ChatTool beans và phân loại theo tên.
 *
 * Mapping role → tools:
 *   ROLE_GUEST (không phải RoleType nhưng xử lý qua enum ảo):
 *     → [SearchWarehousesTool, GetWarehouseDetailTool, AskLoginPromptTool]
 *   ROLE_TENANT → GUEST + [GetMyContractsTool, GetContractDetailTool, GetMyStockTool, GetMyWalletTool]
 *   ROLE_OWNER  → [GetMyWarehousesTool, GetWarehouseBookingsTool, GetRevenueSummaryTool, GetOccupancyTool]
 *   ROLE_STAFF  → [GetAssignedStockTool, GetPendingInboundTool, GetPendingOutboundTool]
 *   ROLE_ADMIN  → [GetPlatformSummaryTool, GetMonthlyRevenueTool]
 *   ROLE_INSPECTOR → [GetMyInspectionsTool, GetInspectionDetailTool]
 */
@Slf4j
@Component
public class ChatToolRegistry {

    // Dùng String key vì GUEST không có trong RoleType
    private static final String GUEST_KEY = "GUEST";

    private final Map<String, List<ChatTool>> toolsByRole = new java.util.HashMap<>();
    private final Map<String, ChatTool> toolsByName = new java.util.HashMap<>();

    // Tool names theo từng role
    private static final List<String> GUEST_TOOL_NAMES = List.of(
            "searchWarehouses", "getWarehouseDetail", "askLoginPrompt", "searchSystemPolicy"
    );
    private static final List<String> TENANT_EXTRA_TOOL_NAMES = List.of(
            "getMyContracts", "getContractDetail", "getMyStock", "getMyWallet"
    );
    private static final List<String> OWNER_TOOL_NAMES = List.of(
            "getMyWarehouses", "getWarehouseBookings", "getRevenueSummary", "getOccupancyRate"
    );
    private static final List<String> STAFF_TOOL_NAMES = List.of(
            "getAssignedStock", "getPendingInbound", "getPendingOutbound"
    );
    private static final List<String> ADMIN_TOOL_NAMES = List.of(
            "getPlatformSummary", "getMonthlyRevenue"
    );
    private static final List<String> INSPECTOR_TOOL_NAMES = List.of(
            "getMyInspections", "getInspectionDetail"
    );

    public ChatToolRegistry(List<ChatTool> allTools) {
        // Index tất cả tools theo tên
        for (ChatTool tool : allTools) {
            toolsByName.put(tool.getName(), tool);
            log.info("[ChatToolRegistry] Registered tool: {}", tool.getName());
        }

        // Phân loại theo role
        toolsByRole.put(GUEST_KEY,              buildToolList(GUEST_TOOL_NAMES));
        toolsByRole.put(RoleType.ROLE_TENANT.name(), buildToolList(mergeLists(GUEST_TOOL_NAMES, TENANT_EXTRA_TOOL_NAMES)));
        toolsByRole.put(RoleType.ROLE_OWNER.name(),  buildToolList(OWNER_TOOL_NAMES));
        toolsByRole.put(RoleType.ROLE_STAFF.name(),  buildToolList(STAFF_TOOL_NAMES));
        toolsByRole.put(RoleType.ROLE_ADMIN.name(),  buildToolList(ADMIN_TOOL_NAMES));
        toolsByRole.put(RoleType.ROLE_INSPECTOR.name(), buildToolList(INSPECTOR_TOOL_NAMES));
    }

    /**
     * Lấy danh sách tools cho role.
     *
     * @param roleName  Tên role từ JWT (VD: "ROLE_TENANT") hoặc "GUEST"
     */
    public List<ChatTool> getToolsForRole(String roleName) {
        return toolsByRole.getOrDefault(roleName, toolsByRole.get(GUEST_KEY));
    }

    /**
     * Tìm tool theo tên (Gemini gọi tên này trong function_call).
     */
    public Optional<ChatTool> findByName(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private List<ChatTool> buildToolList(List<String> names) {
        List<ChatTool> tools = new ArrayList<>();
        for (String name : names) {
            ChatTool tool = toolsByName.get(name);
            if (tool != null) {
                tools.add(tool);
            } else {
                log.warn("[ChatToolRegistry] Tool not found (not yet implemented): {}", name);
            }
        }
        return tools;
    }

    private <T> List<T> mergeLists(List<T> a, List<T> b) {
        List<T> merged = new ArrayList<>(a);
        merged.addAll(b);
        return merged;
    }
}
