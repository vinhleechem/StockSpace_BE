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
            "searchSystemPolicy",
            "getServicePackages",
            "askLoginPrompt",
            "getMyContracts",
            "getContractDetail",
            "getMyStock",
            "getMyWallet",
            "getMyActiveSubscription"
    );

    @Test
    void tenantGetsPrivateReadToolsWhileGuestAndOtherRolesDoNot() {
        ChatToolRegistry registry = new ChatToolRegistry(requiredTools());

        List<String> guest = names(registry.getToolsForRole("GUEST"));
        List<String> tenant = names(registry.getToolsForRole("ROLE_TENANT"));
        List<String> owner = names(registry.getToolsForRole("ROLE_OWNER"));
        List<String> staff = names(registry.getToolsForRole("ROLE_STAFF"));

        assertTrue(guest.contains("askLoginPrompt"));
        assertFalse(guest.contains("getMyWallet"));
        assertTrue(tenant.containsAll(List.of(
                "getMyContracts",
                "getContractDetail",
                "getMyStock",
                "getMyWallet",
                "getMyActiveSubscription"
        )));
        assertTrue(guest.contains("getServicePackages"));
        assertFalse(tenant.contains("askLoginPrompt"));
        assertTrue(owner.isEmpty());
        assertTrue(staff.isEmpty());
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
