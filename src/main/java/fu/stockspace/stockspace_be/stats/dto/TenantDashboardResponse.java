package fu.stockspace.stockspace_be.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDashboardResponse {
    private long activeWarehouseCount;
    private long activeContractCount;
    private long pendingContractCount;
    private long productCount;
    private long stockBatchCount;
    private long totalStockQuantity;
    private long pendingInboundReceiptCount;
    private long pendingOutboundReceiptCount;
    private long pendingAuditCount;
    private long pendingTransferCount;
    private long activeStaffCount;
    private long unreadNotificationCount;
    private TenantSubscriptionSummaryResponse activeSubscription;
}
