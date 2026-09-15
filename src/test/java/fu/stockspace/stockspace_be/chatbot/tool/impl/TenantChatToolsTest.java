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
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.WarehouseStockOverviewResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;

@ExtendWith(MockitoExtension.class)
class TenantChatToolsTest {

    @Mock
    private ContractService contractService;

    @Mock
    private WalletService walletService;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private TenantWarehouseAccessService accessService;

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
                .pricingType(fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType.PER_SQUARE_METER_MONTHLY)
                .rentalPriceSnapshot(new BigDecimal("200000"))
                .leasedAreaM2(new BigDecimal("80"))
                .finalMonthlyRent(new BigDecimal("16000000"))
                .canConfirm(true)
                .canRequestChanges(true)
                .canReject(true)
                .canViewLayout(true)
                .canManageWms(true)
                .tenantId(userId)
                .tenantName("Tenant Secret")
                .tenantEmail("tenant@example.com")
                .ownerId(UUID.randomUUID())
                .ownerName("Owner Secret")
                .build();
        when(contractService.getMyContractsAsTenant(userId, 0, 10))
                .thenReturn(new PageImpl<>(List.of(contract), PageRequest.of(0, 10), 1));

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
        assertEquals("16000000", result.at("/contracts/0/finalMonthlyRent").asText());
        assertTrue(result.at("/contracts/0/canConfirm").asBoolean());
        assertTrue(result.at("/contracts/0/canManageWms").asBoolean());
        verify(contractService).getMyContractsAsTenant(userId, 0, 10);
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
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("8"))
                .leasedHeight(new BigDecimal("4"))
                .leasedAreaM2(new BigDecimal("80"))
                .finalMonthlyRent(new BigDecimal("16000000"))
                .paperContractFiles(List.of("https://example.com/contract.pdf"))
                .ownerNote("Đọc kỹ phụ lục")
                .canViewLayout(true)
                .canManageWms(true)
                .build();
        when(contractService.getContractById(contractId, userId)).thenReturn(contract);

        JsonNode result = objectMapper.readTree(
                new GetContractDetailTool(objectMapper, contractService)
                        .execute(Map.of("contractId", contractId.toString()), userId));

        assertEquals(contractId.toString(), result.get("id").asText());
        assertEquals("Kho B", result.get("warehouseName").asText());
        assertEquals("Đang có hiệu lực", result.get("status").asText());
        assertEquals("80", result.get("leasedAreaM2").asText());
        assertEquals("16000000", result.get("finalMonthlyRent").asText());
        assertEquals("https://example.com/contract.pdf", result.at("/paperContractFiles/0").asText());
        assertEquals("Đọc kỹ phụ lục", result.get("ownerNote").asText());
        assertTrue(result.get("canManageWms").asBoolean());
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
        when(stockBatchService.getStockOverviewByWarehouse(
                eq(userId), eq(warehouseId), any(PageRequest.class)))
                .thenReturn(PagedResponse.<WarehouseStockOverviewResponse>builder()
                        .content(List.of(WarehouseStockOverviewResponse.builder()
                                .skuCode("SKU-01")
                                .skuName("Sản phẩm A")
                                .categoryName("Hàng khô")
                                .uomSymbol("THUNG")
                                .totalQuantity(350)
                                .totalWeightKg(new BigDecimal("700"))
                                .totalVolumeM3(new BigDecimal("12.5"))
                                .build()))
                        .totalElements(1)
                        .build());

        JsonNode result = objectMapper.readTree(
                new GetMyStockTool(objectMapper, stockBatchService, accessService)
                        .executeWithContext(Map.of(), new ChatRequestContext(
                                userId, warehouseId)));

        assertEquals("Kho C", result.get("warehouseName").asText());
        assertEquals(4, result.get("productCount").asLong());
        assertEquals(9, result.get("batchCount").asLong());
        assertEquals(350, result.get("totalQuantity").asLong());
        assertEquals("SKU-01", result.at("/products/0/skuCode").asText());
        assertEquals("700", result.at("/products/0/weightKg").asText());
        assertFalse(result.has("warehouseId"));
        verify(stockBatchService).getStockSummaryByWarehouse(userId, warehouseId);
        verify(stockBatchService).getStockOverviewByWarehouse(
                eq(userId), eq(warehouseId), any(PageRequest.class));
    }

    @Test
    void getMyStockRejectsGuestBeforeCallingService() throws Exception {
        JsonNode result = objectMapper.readTree(
                new GetMyStockTool(objectMapper, stockBatchService, accessService)
                        .execute(Map.of("warehouseId", UUID.randomUUID().toString()), null));

        assertTrue(result.has("error"));
        verifyNoInteractions(stockBatchService);
    }

    @Test
    void privateToolDescriptionsUseVietnameseBusinessTerms() {
        List<String> descriptions = List.of(
                new GetMyContractsTool(objectMapper, contractService).getDescription(),
                new GetContractDetailTool(objectMapper, contractService).getDescription(),
                new GetMyStockTool(objectMapper, stockBatchService, accessService).getDescription(),
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
