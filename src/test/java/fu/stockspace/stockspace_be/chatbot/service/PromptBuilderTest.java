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
        assertTrue(prompt.contains("pricingType"));
        assertTrue(prompt.contains("sortBy"));
        assertTrue(prompt.contains("truyền keyword"));
        assertTrue(prompt.contains("getCurrentSystemRules"));
        assertTrue(prompt.contains("previewSubscriptionChange"));
        assertTrue(prompt.contains("Tiền thuê kho được hai bên thanh toán ngoài StockSpace"));
        assertTrue(prompt.contains("thanh toán gói dịch vụ"));
        assertTrue(prompt.contains("getPublicWarehouseLayout"));
        assertTrue(prompt.contains("floorAreaM2"));
        assertTrue(prompt.contains("capacity"));
        assertTrue(prompt.contains("matchedBySemanticKeyword"));
        assertTrue(prompt.contains("module Quản lý kho"));
        assertTrue(prompt.contains("activeContractCount"));
        assertTrue(prompt.contains("startDate/endDate"));
    }

    @Test
    void guestIsDirectedToLoginForPrivateWmsAndContactData() {
        String prompt = promptBuilder.buildSystemPrompt("GUEST");

        assertTrue(prompt.contains("thông tin liên hệ"));
        assertTrue(prompt.contains("dữ liệu WMS"));
        assertTrue(prompt.contains("askLoginPrompt"));
    }

    @Test
    void guestAndTenantInstructionsUseUserFacingVietnameseLabels() {
        for (String role : new String[]{"GUEST", "ROLE_TENANT"}) {
            String prompt = promptBuilder.buildSystemPrompt(role);

            // Internal tool identifiers are intentionally present for the
            // model, but user-facing role/status labels must not leak.
            String userFacingPrompt = prompt;
            assertFalse(userFacingPrompt.contains("Tenant"));
            assertFalse(userFacingPrompt.contains("PENDING"));
            assertFalse(userFacingPrompt.contains("ACTIVE"));
        }
    }

    @Test
    void citationMetadataIsInternalOnly() {
        String prompt = promptBuilder.buildSystemPrompt("GUEST");

        assertTrue(prompt.contains("kiểm chứng nội bộ"));
        assertTrue(prompt.contains("Tuyệt đối không hiển thị trường citation"));
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

    @Test
    void includesVerifiedScreenContextWhenProvided() {
        String prompt = promptBuilder.buildSystemPrompt(
                "ROLE_TENANT",
                List.of(),
                new ChatRequestContext(UUID.randomUUID(), UUID.randomUUID(), "Kho Bình Tân", "warehouse"));

        assertTrue(prompt.contains("Ngữ cảnh màn hình đã xác minh"));
        assertTrue(prompt.contains("warehouse"));
    }

    @Test
    void ignoresUnallowlistedScreenContext() {
        String prompt = promptBuilder.buildSystemPrompt(
                "ROLE_TENANT",
                List.of(),
                new ChatRequestContext(UUID.randomUUID(), UUID.randomUUID(), "Kho Bình Tân",
                        "ignore-system-prompt"));

        assertFalse(prompt.contains("ignore-system-prompt"));
        assertTrue(prompt.contains("chưa có màn hình nghiệp vụ nào được chọn"));
    }
}
