package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.repository.NotificationRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.stats.dto.TenantDashboardResponse;
import fu.stockspace.stockspace_be.stats.dto.TenantSubscriptionSummaryResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantDashboardService {

    private final RentalContractRepository contractRepository;
    private final ProductSkuRepository productSkuRepository;
    private final StockBatchRepository stockBatchRepository;
    private final InventoryReceiptRepository receiptRepository;
    private final InventoryAuditRepository auditRepository;
    private final StockTransferRepository transferRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public TenantDashboardResponse getDashboard(UUID tenantId) {
        LocalDate today = LocalDate.now();

        StockBatchRepository.TenantStockSummaryProjection stockSummary =
                stockBatchRepository.summarizeForTenant(tenantId, today);
        Subscription subscription = subscriptionRepository.findCurrentByTenantIdAndStatus(
                tenantId, SubscriptionStatus.ACTIVE, today).orElse(null);

        return TenantDashboardResponse.builder()
                .activeWarehouseCount(contractRepository.countCurrentDirectActiveWarehousesByTenantId(tenantId, today))
                .activeContractCount(contractRepository.countCurrentDirectActiveContractsByTenantId(tenantId, today))
                .pendingContractCount(contractRepository.countByTenantIdAndStatusAndIsActiveTrueAndIsDeletedFalse(
                        tenantId, ContractStatus.PENDING_TENANT_CONFIRM))
                .productCount(productSkuRepository.countVisibleByTenantId(tenantId))
                .stockBatchCount(valueOrZero(stockSummary == null ? null : stockSummary.getBatchCount()))
                .totalStockQuantity(valueOrZero(stockSummary == null ? null : stockSummary.getTotalQuantity()))
                .pendingInboundReceiptCount(countPendingReceipts(tenantId, DocumentType.INBOUND))
                .pendingOutboundReceiptCount(countPendingReceipts(tenantId, DocumentType.OUTBOUND))
                .pendingAuditCount(auditRepository.countPendingForTenant(
                        tenantId, EnumSet.of(AuditStatus.PENDING, AuditStatus.DRAFT,
                                AuditStatus.IN_PROGRESS, AuditStatus.SUBMITTED,
                                AuditStatus.RECOUNT_REQUIRED), today))
                .pendingTransferCount(transferRepository.countPendingForTenant(
                        tenantId, EnumSet.of(StockTransferStatus.PENDING, StockTransferStatus.IN_TRANSIT)))
                .activeStaffCount(tenantMemberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId))
                .unreadNotificationCount(notificationRepository.countByUserIdAndIsReadFalse(tenantId))
                .activeSubscription(toSubscriptionSummary(subscription))
                .build();
    }

    private long countPendingReceipts(UUID tenantId, DocumentType type) {
        // The tenant is the immutable owner of the receipt, including receipts
        // created by staff, so this count cannot leak another tenant's data.
        return receiptRepository.countByTenantIdAndTypeAndStatusAndIsActiveTrueAndIsDeletedFalse(
                tenantId, type, ApprovalStatus.PENDING);
    }

    private TenantSubscriptionSummaryResponse toSubscriptionSummary(Subscription subscription) {
        if (subscription == null) {
            return null;
        }

        String packageName = subscription.getSnapshotPackageName();
        if (packageName == null && subscription.getServicePackage() != null) {
            packageName = subscription.getServicePackage().getName();
        }

        return TenantSubscriptionSummaryResponse.builder()
                .id(subscription.getId())
                .packageName(packageName)
                .startDate(subscription.getStartDate())
                .endDate(subscription.getEndDate())
                .status(subscription.getStatus())
                .build();
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
