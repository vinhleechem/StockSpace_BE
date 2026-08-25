package fu.stockspace.stockspace_be.chatbot.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.impl.*;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;

import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.service.InspectionService;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.wallet.entity.Wallet;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse;

import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevBToolsTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock private WarehouseRepository warehouseRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TenantMemberRepository tenantMemberRepository;
    @Mock private StockBatchService stockBatchService;
    @Mock private InventoryReceiptService receiptService;
    @Mock private UserRepository userRepository;
    @Mock private BookingRequestRepository bookingRepository;
    @Mock private RentalContractRepository contractRepository;
    @Mock private InspectionService inspectionService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void testGetMyWarehousesTool_Success() {
        GetMyWarehousesTool tool = new GetMyWarehousesTool(objectMapper, warehouseRepository);
        Warehouse w = Warehouse.builder().id(UUID.randomUUID()).name("Kho A").status(WarehouseStatus.AVAILABLE).build();
        when(warehouseRepository.findByOwnerId(eq(userId), any())).thenReturn(new PageImpl<>(List.of(w)));

        String json = tool.execute(Collections.emptyMap(), userId);
        assertTrue(json.contains("Kho A"));
    }

    @Test
    void testGetRevenueSummaryTool_Success() {
        GetRevenueSummaryTool tool = new GetRevenueSummaryTool(objectMapper, walletRepository, transactionRepository);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).build();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findMonthlyRevenueByWalletIdAndTypesAndYear(any(), any(), any(Integer.class)))
                .thenReturn(Collections.emptyList());

        String json = tool.execute(Collections.emptyMap(), userId);
        assertTrue(json.contains("totalRevenue"));
    }

    @Test
    void testGetOccupancyTool_Success() {
        GetOccupancyTool tool = new GetOccupancyTool(objectMapper, warehouseRepository, contractRepository);
        Warehouse w = Warehouse.builder().id(UUID.randomUUID()).name("Kho A").status(WarehouseStatus.AVAILABLE).build();
        when(warehouseRepository.findByOwnerId(eq(userId), any())).thenReturn(new PageImpl<>(List.of(w)));
        when(contractRepository.findCurrentDirectActiveWarehouseIdsByOwnerId(eq(userId), any()))
                .thenReturn(List.of(w.getId()));
        when(contractRepository.countCurrentDirectActiveContractsByOwnerId(eq(userId), any())).thenReturn(2L);
        when(contractRepository.countDistinctCurrentDirectActiveTenantsByOwnerId(eq(userId), any())).thenReturn(2L);

        String json = tool.execute(Collections.emptyMap(), userId);
        assertTrue(json.contains("occupancyRatePercentage"));
        assertTrue(json.contains("100.0"));
        assertTrue(json.contains("activeTenantCount"));
    }

    @Test
    void testGetAssignedStockTool_Success() {
        GetAssignedStockTool tool = new GetAssignedStockTool(objectMapper, tenantMemberRepository, stockBatchService);
        fu.stockspace.stockspace_be.auth.entity.User tenantUser = fu.stockspace.stockspace_be.auth.entity.User.builder().id(UUID.randomUUID()).build();
        TenantMember member = TenantMember.builder().id(UUID.randomUUID()).tenant(tenantUser).build();
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(userId)).thenReturn(Optional.of(member));

        UUID warehouseId = UUID.randomUUID();
        PagedResponse<fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse> paged =
                PagedResponse.<fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse>builder().content(Collections.emptyList()).build();
        when(stockBatchService.getStockByWarehouse(eq(tenantUser.getId()), eq(warehouseId), eq(userId), any()))
                .thenReturn(paged);

        String json = tool.execute(java.util.Map.of("warehouseId", warehouseId.toString()), userId);
        assertNotNull(json);
    }

    @Test
    void testGetPendingInboundTool_Success() {
        GetPendingInboundTool tool = new GetPendingInboundTool(objectMapper, receiptService);
        PagedResponse<fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse> paged =
                PagedResponse.<fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse>builder().content(Collections.emptyList()).build();
        when(receiptService.getReceiptsByWarehouse(any(), any(), any(), any())).thenReturn(paged);

        String json = tool.executeWithContext(Collections.emptyMap(), new ChatRequestContext(
                userId, "ROLE_STAFF", UUID.randomUUID()));
        assertNotNull(json);
    }


    @Test
    void testGetPlatformSummaryTool_Success() {
        GetPlatformSummaryTool tool = new GetPlatformSummaryTool(objectMapper, userRepository, warehouseRepository, bookingRepository, contractRepository);
        when(userRepository.count()).thenReturn(10L);
        when(warehouseRepository.count()).thenReturn(5L);

        String json = tool.execute(Collections.emptyMap(), userId);
        assertTrue(json.contains("totalUsers"));
        assertTrue(json.contains("10"));
    }

    @Test
    void testGetMyInspectionsTool_Success() {
        GetMyInspectionsTool tool = new GetMyInspectionsTool(objectMapper, inspectionService);
        when(inspectionService.getAssignedInspections(eq(userId), eq(0), eq(50)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        String json = tool.execute(Collections.emptyMap(), userId);
        assertNotNull(json);
    }

}
