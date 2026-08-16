package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.SubscriptionResponse;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.wallet.dto.WalletResponse;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantChatToolsTest {

    @Mock
    private ContractService contractService;

    @Mock
    private WalletService walletService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private StockBatchService stockBatchService;

    private ObjectMapper objectMapper;
    private UUID userId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        userId = UUID.randomUUID();
    }

    @Test
    void getMyContractsUsesTenantServiceAndRemovesPii() throws Exception {
        UUID contractId = UUID.randomUUID();
        RentalContractResponse contract = RentalContractResponse.builder()
                .id(contractId)
                .status("ACTIVE")
                .warehouseId(UUID.randomUUID())
                .warehouseName("Kho A")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .depositAmount(new BigDecimal("5000000"))
                .tenantId(userId)
                .tenantName("Tenant Secret")
                .tenantEmail("tenant@example.com")
                .ownerId(UUID.randomUUID())
                .ownerName("Owner Secret")
                .paperContractImages("[\"private.jpg\"]")
                .cancelEvidence("[\"evidence.jpg\"]")
                .build();
        when(contractService.getMyContractsAsTenant(userId, 0, 20))
                .thenReturn(new PageImpl<>(List.of(contract), PageRequest.of(0, 20), 1));

        JsonNode result = objectMapper.readTree(
                new GetMyContractsTool(objectMapper, contractService).execute(Map.of(), userId));

        assertEquals(1, result.get("total").asLong());
        assertEquals(contractId.toString(), result.at("/contracts/0/id").asText());
        assertEquals("Kho A", result.at("/contracts/0/warehouseName").asText());
        assertEquals("Đang có hiệu lực", result.at("/contracts/0/status").asText());
        assertFalse(result.toString().contains("ACTIVE"));
        assertFalse(result.toString().contains("tenantEmail"));
        assertFalse(result.toString().contains("tenantName"));
        assertFalse(result.toString().contains("ownerName"));
        assertFalse(result.toString().contains("paperContractImages"));
        assertFalse(result.toString().contains("cancelEvidence"));
        verify(contractService).getMyContractsAsTenant(userId, 0, 20);
    }

    @Test
    void getMyContractsRejectsGuestBeforeCallingService() throws Exception {
        JsonNode result = objectMapper.readTree(
                new GetMyContractsTool(objectMapper, contractService).execute(Map.of(), null));

        assertTrue(result.has("error"));
        verifyNoInteractions(contractService);
    }

    @Test
    void getContractDetailUsesAuthorizedServiceAndRemovesPii() throws Exception {
        UUID contractId = UUID.randomUUID();
        RentalContractResponse contract = RentalContractResponse.builder()
                .id(contractId)
                .status("ACTIVE")
                .warehouseId(UUID.randomUUID())
                .warehouseName("Kho B")
                .warehouseAddress("Quận 7")
                .tenantName("Tenant Secret")
                .tenantEmail("tenant@example.com")
                .ownerName("Owner Secret")
                .tenantConfirmed(true)
                .ownerConfirmed(false)
                .build();
        when(contractService.getContractById(contractId, userId)).thenReturn(contract);

        JsonNode result = objectMapper.readTree(
                new GetContractDetailTool(objectMapper, contractService)
                        .execute(Map.of("contractId", contractId.toString()), userId));

        assertEquals(contractId.toString(), result.get("id").asText());
        assertEquals("Kho B", result.get("warehouseName").asText());
        assertEquals("Đang có hiệu lực", result.get("status").asText());
        assertTrue(result.get("nguoiThueDaXacNhan").asBoolean());
        assertFalse(result.get("chuKhoDaXacNhan").asBoolean());
        assertFalse(result.has("tenantConfirmed"));
        assertFalse(result.has("ownerConfirmed"));
        assertFalse(result.toString().contains("ACTIVE"));
        assertFalse(result.toString().contains("tenantEmail"));
        assertFalse(result.toString().contains("tenantName"));
        assertFalse(result.toString().contains("ownerName"));
        verify(contractService).getContractById(contractId, userId);
    }

    @Test
    void getContractDetailRejectsGuestBeforeParsingParameters() throws Exception {
        JsonNode result = objectMapper.readTree(
                new GetContractDetailTool(objectMapper, contractService)
                        .execute(Map.of("contractId", "not-a-uuid"), null));

        assertTrue(result.has("error"));
        verifyNoInteractions(contractService);
    }

    @Test
    void getMyWalletReturnsOnlyMinimalFinancialData() throws Exception {
        WalletResponse wallet = WalletResponse.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .balance(new BigDecimal("123456.78"))
                .updatedAt(LocalDateTime.of(2026, 7, 28, 10, 30))
                .build();
        when(walletService.getWalletInfo(userId)).thenReturn(wallet);

        JsonNode result = objectMapper.readTree(
                new GetMyWalletTool(objectMapper, walletService).execute(Map.of(), userId));

        assertEquals(new BigDecimal("123456.78"), result.get("balance").decimalValue());
        assertEquals("VND", result.get("currency").asText());
        assertFalse(result.has("id"));
        assertFalse(result.has("userId"));
        verify(walletService).getWalletInfo(userId);
    }

    @Test
    void getMyWalletRejectsGuestBeforeCallingService() throws Exception {
        JsonNode result = objectMapper.readTree(
                new GetMyWalletTool(objectMapper, walletService).execute(Map.of(), null));

        assertTrue(result.has("error"));
        verifyNoInteractions(walletService);
    }

    @Test
    void getMyActiveSubscriptionReturnsPackageDetailsWithoutInternalIds() throws Exception {
        SubscriptionResponse subscription = SubscriptionResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(userId)
                .servicePackage(ServicePackageResponse.builder()
                        .id(UUID.randomUUID())
                        .name("Cơ bản")
                        .features("Quản lý 2 kho")
                        .price(new BigDecimal("199000"))
                        .durationDays(30)
                        .maxStaff(2)
                        .build())
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionService.getMyActiveSubscription(userId)).thenReturn(subscription);

        JsonNode result = objectMapper.readTree(
                new GetMyActiveSubscriptionTool(objectMapper, subscriptionService)
                        .execute(Map.of(), userId));

        assertEquals("Cơ bản", result.at("/servicePackage/name").asText());
        assertEquals(2, result.at("/servicePackage/maxStaff").asInt());
        assertFalse(result.toString().contains("tenantId"));
        assertFalse(result.toString().contains("\"id\""));
        verify(subscriptionService).getMyActiveSubscription(userId);
    }

    @Test
    void getMyActiveSubscriptionRejectsGuestBeforeCallingService() throws Exception {
        JsonNode result = objectMapper.readTree(
                new GetMyActiveSubscriptionTool(objectMapper, subscriptionService).execute(Map.of(), null));

        assertTrue(result.has("error"));
        verifyNoInteractions(subscriptionService);
    }

    @Test
    void getMyStockDelegatesTenantAndWarehouseToSecuredService() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        StockBatchService.WarehouseStockSummary summary =
                new StockBatchService.WarehouseStockSummary(warehouseId, "Kho C", 4, 9, 350);
        when(stockBatchService.getStockSummaryByWarehouse(userId, warehouseId)).thenReturn(summary);

        JsonNode result = objectMapper.readTree(
                new GetMyStockTool(objectMapper, stockBatchService)
                        .executeWithContext(Map.of(), new ChatRequestContext(
                                userId, "ROLE_TENANT", warehouseId)));

        assertEquals("Kho C", result.get("warehouseName").asText());
        assertEquals(4, result.get("productCount").asLong());
        assertEquals(9, result.get("batchCount").asLong());
        assertEquals(350, result.get("totalQuantity").asLong());
        assertFalse(result.has("warehouseId"));
        verify(stockBatchService).getStockSummaryByWarehouse(userId, warehouseId);
    }

    @Test
    void getMyStockRejectsGuestBeforeCallingService() throws Exception {
        JsonNode result = objectMapper.readTree(
                new GetMyStockTool(objectMapper, stockBatchService)
                        .execute(Map.of("warehouseId", UUID.randomUUID().toString()), null));

        assertTrue(result.has("error"));
        verifyNoInteractions(stockBatchService);
    }

    @Test
    void privateToolDescriptionsUseVietnameseBusinessTerms() {
        List<String> descriptions = List.of(
                new GetMyContractsTool(objectMapper, contractService).getDescription(),
                new GetContractDetailTool(objectMapper, contractService).getDescription(),
                new GetMyStockTool(objectMapper, stockBatchService).getDescription(),
                new GetMyWalletTool(objectMapper, walletService).getDescription()
        );

        descriptions.forEach(description -> {
            String normalized = description.toUpperCase(Locale.ROOT);
            assertFalse(normalized.contains("TENANT"));
            assertFalse(normalized.contains("OWNER"));
            assertFalse(normalized.contains("ACTIVE"));
            assertFalse(normalized.contains("PENDING"));
        });
    }
}
