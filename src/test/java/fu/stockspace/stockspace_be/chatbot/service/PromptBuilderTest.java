package fu.stockspace.stockspace_be.chatbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    @Test
    void promptRequiresUserFriendlyOutputWithoutInternalIdentifiers() {
        String prompt = promptBuilder.buildSystemPrompt("GUEST");

        assertTrue(prompt.contains("không để lộ tên biến cấu hình"));
        assertTrue(prompt.contains("Dịch các mã trạng thái nội bộ sang tiếng Việt"));
        assertFalse(prompt.contains("deposit_percentage"));
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
}
