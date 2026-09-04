package fu.stockspace.stockspace_be.chatbot.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatToolRegistryTest {

    private static final List<String> REQUIRED_NAMES = List.of(
            "searchWarehouses",
            "getWarehouseDetail",
            "getPublicWarehouseLayout",
            "getWarehouseTypes",
            "searchSystemPolicy",
            "getCurrentSystemRules",
            "getServicePackages",
            "askLoginPrompt",
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
    );

    @Test
    void tenantGetsPrivateReadToolsWhileGuestAndUnknownRolesDoNot() {
        ChatToolRegistry registry = new ChatToolRegistry(requiredTools());

        List<String> guest = names(registry.getToolsForRole("GUEST"));
        List<String> tenant = names(registry.getToolsForRole("ROLE_TENANT"));
        List<String> unsupported = names(registry.getToolsForRole("ROLE_UNSUPPORTED"));

        assertTrue(guest.contains("askLoginPrompt"));
        assertFalse(guest.contains("getMyWallet"));
        assertTrue(tenant.containsAll(List.of(
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
        )));
        assertTrue(guest.contains("getServicePackages"));
        assertTrue(guest.containsAll(List.of(
                "getPublicWarehouseLayout",
                "getWarehouseTypes",
                "getCurrentSystemRules")));
        assertFalse(tenant.contains("askLoginPrompt"));
        assertTrue(unsupported.isEmpty());
    }


    @Test
    void failsFastWhenRequiredToolIsMissingOrDuplicated() {
        List<ChatTool> missing = requiredTools();
        missing.remove(missing.size() - 1);
        assertThrows(
                IllegalStateException.class,
                () -> new ChatToolRegistry(missing)
        );

        List<ChatTool> duplicated = requiredTools();
        duplicated.add(namedTool("searchWarehouses"));
        assertThrows(
                IllegalStateException.class,
                () -> new ChatToolRegistry(duplicated)
        );
    }

    private List<ChatTool> requiredTools() {
        List<ChatTool> tools = new ArrayList<>();
        REQUIRED_NAMES.forEach(name -> tools.add(namedTool(name)));
        return tools;
    }

    private ChatTool namedTool(String name) {
        ChatTool tool = mock(ChatTool.class);
        when(tool.getName()).thenReturn(name);
        return tool;
    }

    private List<String> names(List<ChatTool> tools) {
        return tools.stream().map(ChatTool::getName).toList();
    }
}
