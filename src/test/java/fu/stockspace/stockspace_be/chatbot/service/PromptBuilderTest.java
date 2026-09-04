package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void promptRequiresUserFriendlyOutputWithoutInternalIdentifiers() {
        String prompt = promptBuilder.buildSystemPrompt("GUEST");

        assertTrue(prompt.contains("không để lộ tên biến cấu hình"));
        assertTrue(prompt.contains("Dịch các mã trạng thái nội bộ sang tiếng Việt"));
        assertTrue(prompt.contains("ý định của người dùng"));
        assertTrue(prompt.contains("searchWarehouses với"));
        assertTrue(prompt.contains("các tham số rỗng"));
        assertFalse(prompt.contains("cứ list ra"));
        assertTrue(prompt.contains("getServicePackages"));
        assertTrue(prompt.contains("getMyActiveSubscription"));
        assertTrue(prompt.contains("getInventoryReceipts"));
        assertTrue(prompt.contains("getInventoryAudits"));
        assertTrue(prompt.contains("getStockTransfers"));
        assertTrue(prompt.contains("getWarehouseCapacity"));
        assertTrue(prompt.contains("getCurrentSystemRules"));
        assertTrue(prompt.contains("previewSubscriptionChange"));
        assertTrue(prompt.contains("Tiền thuê kho được hai bên thanh toán ngoài StockSpace"));
        assertTrue(prompt.contains("thanh toán gói dịch vụ"));
    }

    @Test
    void guestIsDirectedToLoginForPrivateWmsAndContactData() {
        String prompt = promptBuilder.buildSystemPrompt("GUEST");

        assertTrue(prompt.contains("thông tin liên hệ"));
        assertTrue(prompt.contains("phiếu nhập xuất"));
        assertTrue(prompt.contains("chuyển kho"));
        assertTrue(prompt.contains("askLoginPrompt"));
    }

    @Test
    void guestAndTenantInstructionsUseUserFacingVietnameseLabels() {
        for (String role : new String[]{"GUEST", "ROLE_TENANT"}) {
            String prompt = promptBuilder.buildSystemPrompt(role);

            assertFalse(prompt.contains("Tenant"));
            assertFalse(prompt.contains("PENDING"));
            assertFalse(prompt.contains("ACTIVE"));
        }
    }

    @Test
    void includesVerifiedWarehouseNamePerRequestInsteadOfHardCodingOne() {
        String prompt = promptBuilder.buildSystemPrompt(
                "ROLE_TENANT",
                List.of(),
                new ChatRequestContext(
                        UUID.randomUUID(), UUID.randomUUID(), "Kho Bình Tân"));

        assertTrue(prompt.contains("Kho Bình Tân"));
        assertTrue(prompt.contains("Ngữ cảnh kho đã xác minh"));
    }
}
