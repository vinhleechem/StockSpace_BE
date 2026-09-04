package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.dto.SystemPolicyResponse;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.service.SystemConfigService;
import fu.stockspace.stockspace_be.common.service.SystemPolicyService;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionPreviewResponse;
import fu.stockspace.stockspace_be.subscription.service.ServicePackageService;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawResponse;
import fu.stockspace.stockspace_be.wallet.service.TransactionService;
import fu.stockspace.stockspace_be.wallet.service.WithdrawService;
import fu.stockspace.stockspace_be.warehouse.dto.RackResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseBinResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.wms.putaway.PutawayInputItem;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionItem;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionResult;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpandedChatToolsTest {

    @Mock private SystemPolicyService policyService;
    @Mock private SystemConfigService configService;
    @Mock private WarehouseLayoutService layoutService;
    @Mock private TransactionService transactionService;
    @Mock private WithdrawService withdrawService;
    @Mock private ServicePackageService packageService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private PutawaySuggestionService putawaySuggestionService;

    private ObjectMapper objectMapper;
    private UUID userId;
    private UUID warehouseId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        userId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
    }

    @Test
    void currentSystemRulesUsesLivePolicyAndFriendlyConfigNames() throws Exception {
        when(policyService.getActivePolicy()).thenReturn(SystemPolicyResponse.builder()
                .version("2026.09")
                .content("Điều khoản đang hiệu lực")
                .isActive(true)
                .createdAt(LocalDateTime.of(2026, 9, 4, 8, 0))
                .build());
        when(configService.getPublicConfigs()).thenReturn(List.of(
                SystemConfigResponse.builder()
                        .configKey("contract_expiry_days")
                        .configValue("9")
                        .description("Thời hạn xác nhận")
                        .build(),
                SystemConfigResponse.builder()
                        .configKey("inspection_fee")
                        .configValue("55000")
                        .description("Phí kiểm định")
                        .build()));

        String json = new GetCurrentSystemRulesTool(objectMapper, policyService, configService)
                .execute(Map.of(), null);
        JsonNode result = objectMapper.readTree(json);

        assertEquals("2026.09", result.at("/activePolicy/version").asText());
        assertEquals("9", result.at("/publicConfigs/0/value").asText());
        assertEquals("ngày", result.at("/publicConfigs/0/unit").asText());
        assertFalse(json.contains("contract_expiry_days"));
        assertFalse(json.contains("inspection_fee"));
    }

    @Test
    void publicLayoutReturnsSafeStructureWithoutInternalIds() throws Exception {
        UUID layoutId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();
        WarehouseLayoutResponse layout = WarehouseLayoutResponse.builder()
                .id(layoutId)
                .warehouseId(warehouseId)
                .tenantId(UUID.randomUUID())
                .width(new BigDecimal("20"))
                .length(new BigDecimal("30"))
                .height(new BigDecimal("8"))
                .totalRacks(1)
                .totalBins(1)
                .racks(List.of(RackResponse.builder()
                        .id(rackId)
                        .name("Kệ A")
                        .code("A")
                        .bins(List.of(WarehouseBinResponse.builder()
                                .id(binId)
                                .name("Ô A1")
                                .code("A1")
                                .build()))
                        .build()))
                .build();
        when(layoutService.getLayoutTree(warehouseId, null, "PUBLIC")).thenReturn(layout);

        String json = new GetPublicWarehouseLayoutTool(objectMapper, layoutService)
                .execute(Map.of("warehouseId", warehouseId.toString()), null);
        JsonNode result = objectMapper.readTree(json);

        assertEquals("Kệ A", result.at("/racks/0/name").asText());
        assertEquals("Ô A1", result.at("/racks/0/bins/0/name").asText());
        assertFalse(json.contains(layoutId.toString()));
        assertFalse(json.contains(rackId.toString()));
        assertFalse(json.contains(binId.toString()));
    }

    @Test
    void walletWithdrawalHistoryDoesNotExposeBankAccountDetails() throws Exception {
        WithdrawResponse withdrawal = WithdrawResponse.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new BigDecimal("100000"))
                .bankName("Ngân hàng A")
                .bankAccountNumber("123456789")
                .bankAccountHolder("NGUYEN VAN A")
                .status(ApprovalStatus.PENDING)
                .build();
        when(withdrawService.getMyWithdrawRequests(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(withdrawal)));

        String json = new GetMyWalletActivityTool(objectMapper, transactionService, withdrawService)
                .execute(Map.of("view", "WITHDRAWALS"), userId);
        JsonNode result = objectMapper.readTree(json);

        assertEquals("Ngân hàng A", result.at("/withdrawals/0/bankName").asText());
        assertFalse(json.contains("123456789"));
        assertFalse(json.contains("NGUYEN VAN A"));
        assertFalse(json.contains("bankAccountNumber"));
    }

    @Test
    void subscriptionPreviewResolvesCurrentPackageByNameWithoutExposingIds() throws Exception {
        UUID packageId = UUID.randomUUID();
        when(packageService.getAllPackages()).thenReturn(List.of(ServicePackageResponse.builder()
                .id(packageId)
                .name("Pro")
                .price(new BigDecimal("500000"))
                .maxStaff(10)
                .build()));
        when(subscriptionService.previewSubscriptionChange(userId, packageId))
                .thenReturn(SubscriptionPreviewResponse.builder()
                        .currentPackageName("Basic")
                        .newPackageName("Pro")
                        .transactionType("UPGRADE")
                        .canProceed(true)
                        .message("Có thể nâng cấp")
                        .build());

        String json = new PreviewSubscriptionChangeTool(
                objectMapper, packageService, subscriptionService)
                .execute(Map.of("packageName", "pro"), userId);
        JsonNode result = objectMapper.readTree(json);

        assertTrue(result.get("canProceed").asBoolean());
        assertEquals("Pro", result.get("newPackageName").asText());
        assertFalse(json.contains(packageId.toString()));
    }

    @Test
    void putawayPreviewUsesVerifiedWarehouseContextAndDoesNotWrite() throws Exception {
        UUID skuId = UUID.randomUUID();
        when(putawaySuggestionService.suggest(eq(userId), isNull(), eq(warehouseId), any()))
                .thenReturn(new PutawaySuggestionResult(
                        warehouseId,
                        UUID.randomUUID(),
                        List.of(new PutawaySuggestionItem(
                                skuId, "SKU-01", "Sản phẩm A", 12, List.of(), 0, null))));
        ChatRequestContext context = new ChatRequestContext(userId, warehouseId, "Kho hiện tại");

        String json = new SuggestPutawayTool(objectMapper, putawaySuggestionService)
                .executeWithContext(Map.of("items", List.of(Map.of(
                        "skuId", skuId.toString(), "quantity", 12))), context);
        JsonNode result = objectMapper.readTree(json);

        assertEquals("Kho hiện tại", result.get("warehouseName").asText());
        assertEquals("SKU-01", result.at("/items/0/skuCode").asText());
        assertTrue(result.get("notice").asText().contains("không giữ chỗ"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PutawayInputItem>> items = ArgumentCaptor.forClass(List.class);
        verify(putawaySuggestionService).suggest(eq(userId), isNull(), eq(warehouseId), items.capture());
        assertEquals(12, items.getValue().get(0).quantity());
    }
}
