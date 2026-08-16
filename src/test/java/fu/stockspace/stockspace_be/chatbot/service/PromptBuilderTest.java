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
        assertFalse(prompt.contains("deposit_percentage"));
        assertTrue(prompt.contains("getServicePackages"));
        assertTrue(prompt.contains("getMyActiveSubscription"));
    }

    @Test
    void roleInstructionsUseUserFacingVietnameseLabels() {
        for (String role : new String[]{
                "ROLE_TENANT", "ROLE_OWNER", "ROLE_STAFF", "ROLE_ADMIN", "ROLE_INSPECTOR"
        }) {
            String prompt = promptBuilder.buildSystemPrompt(role);

            assertFalse(prompt.contains("Tenant"));
            assertFalse(prompt.contains("Owner"));
            assertFalse(prompt.contains("Staff"));
            assertFalse(prompt.contains("Admin"));
            assertFalse(prompt.contains("Inspector"));
            assertFalse(prompt.contains("PENDING"));
            assertFalse(prompt.contains("ACTIVE"));
        }
    }

    @Test
    void includesVerifiedWarehouseNamePerRequestInsteadOfHardCodingOne() {
        String prompt = promptBuilder.buildSystemPrompt(
                "ROLE_OWNER",
                List.of(),
                new ChatRequestContext(
                        UUID.randomUUID(), "ROLE_OWNER", UUID.randomUUID(), "Kho Bình Tân"));

        assertTrue(prompt.contains("Kho Bình Tân"));
        assertTrue(prompt.contains("Ngữ cảnh kho đã xác minh"));
    }
}
