package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.repository.NotificationRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.stats.dto.TenantDashboardResponse;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantDashboardServiceTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private InventoryReceiptRepository receiptRepository;
    @Mock private InventoryAuditRepository auditRepository;
    @Mock private StockTransferRepository transferRepository;
    @Mock private TenantMemberRepository tenantMemberRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private StockBatchRepository.TenantStockSummaryProjection stockSummary;

    @InjectMocks
    private TenantDashboardService tenantDashboardService;

    @Test
    void returnsTenantScopedDashboardMetricsAndSubscription() {
        UUID tenantId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        when(stockBatchRepository.summarizeForTenant(eq(tenantId), eq(today))).thenReturn(stockSummary);
        when(stockSummary.getBatchCount()).thenReturn(12L);
        when(stockSummary.getTotalQuantity()).thenReturn(345L);
        when(contractRepository.countCurrentDirectActiveWarehousesByTenantId(tenantId, today)).thenReturn(2L);
        when(contractRepository.countCurrentDirectActiveContractsByTenantId(tenantId, today)).thenReturn(3L);
        when(contractRepository.countByTenantIdAndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, ContractStatus.PENDING_TENANT_CONFIRM)).thenReturn(1L);
        when(productSkuRepository.countVisibleByTenantId(tenantId)).thenReturn(8L);
        when(receiptRepository.countByTenantIdAndTypeAndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, DocumentType.INBOUND, ApprovalStatus.PENDING)).thenReturn(4L);
        when(receiptRepository.countByTenantIdAndTypeAndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, DocumentType.OUTBOUND, ApprovalStatus.PENDING)).thenReturn(5L);
        when(auditRepository.countPendingForTenant(eq(tenantId), anyCollection(), eq(today))).thenReturn(2L);
        when(transferRepository.countPendingForTenant(eq(tenantId), anyCollection())).thenReturn(6L);
        when(tenantMemberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId)).thenReturn(7L);
        when(notificationRepository.countByUserIdAndIsReadFalse(tenantId)).thenReturn(9L);

        ServicePackage servicePackage = ServicePackage.builder().name("Pro").build();
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .servicePackage(servicePackage)
                .startDate(today.minusDays(5))
                .endDate(today.plusDays(25))
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findCurrentByTenantIdAndStatus(
                tenantId, SubscriptionStatus.ACTIVE, today)).thenReturn(Optional.of(subscription));

        TenantDashboardResponse response = tenantDashboardService.getDashboard(tenantId);

        assertNotNull(response);
        assertEquals(2L, response.getActiveWarehouseCount());
        assertEquals(3L, response.getActiveContractCount());
        assertEquals(1L, response.getPendingContractCount());
        assertEquals(8L, response.getProductCount());
        assertEquals(12L, response.getStockBatchCount());
        assertEquals(345L, response.getTotalStockQuantity());
        assertEquals(4L, response.getPendingInboundReceiptCount());
        assertEquals(5L, response.getPendingOutboundReceiptCount());
        assertEquals(2L, response.getPendingAuditCount());
        assertEquals(6L, response.getPendingTransferCount());
        assertEquals(7L, response.getActiveStaffCount());
        assertEquals(9L, response.getUnreadNotificationCount());
        assertEquals("Pro", response.getActiveSubscription().getPackageName());
    }

    @Test
    void returnsZeroStockAndNoSubscriptionWhenTenantHasNoInventoryAccessYet() {
        UUID tenantId = UUID.randomUUID();
        when(stockBatchRepository.summarizeForTenant(eq(tenantId), any(LocalDate.class))).thenReturn(null);
        when(subscriptionRepository.findCurrentByTenantIdAndStatus(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class))).thenReturn(Optional.empty());
        when(auditRepository.countPendingForTenant(eq(tenantId), anyCollection(), any(LocalDate.class))).thenReturn(0L);
        when(transferRepository.countPendingForTenant(eq(tenantId), anyCollection())).thenReturn(0L);

        TenantDashboardResponse response = tenantDashboardService.getDashboard(tenantId);

        assertEquals(0L, response.getStockBatchCount());
        assertEquals(0L, response.getTotalStockQuantity());
        org.junit.jupiter.api.Assertions.assertNull(response.getActiveSubscription());
    }
}
