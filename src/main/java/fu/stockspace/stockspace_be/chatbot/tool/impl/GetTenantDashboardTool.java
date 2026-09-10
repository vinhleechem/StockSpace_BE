package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.stats.dto.TenantDashboardResponse;
import fu.stockspace.stockspace_be.stats.dto.TenantSubscriptionSummaryResponse;
import fu.stockspace.stockspace_be.stats.service.TenantDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only summary used when a tenant asks what needs attention across all
 * warehouses. The underlying dashboard service already scopes every count to
 * the authenticated tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetTenantDashboardTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final TenantDashboardService dashboardService;

    @Override
    public String getName() {
        return "getTenantDashboard";
    }

    @Override
    public String getDescription() {
        return "Tóm tắt tình hình tài khoản người thuê trên tất cả kho: hợp đồng, tồn kho, "
                + "phiếu chờ duyệt, kiểm kê, chuyển kho, nhân viên, thông báo và gói dịch vụ hiện tại.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem tổng quan tài khoản.\"}";
        }
        try {
            return objectMapper.writeValueAsString(toSafeMap(dashboardService.getDashboard(userId)));
        } catch (Exception exception) {
            log.warn("[GetTenantDashboardTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy tổng quan tài khoản lúc này.\"}";
        }
    }

    private Map<String, Object> toSafeMap(TenantDashboardResponse dashboard) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeWarehouseCount", dashboard.getActiveWarehouseCount());
        result.put("activeContractCount", dashboard.getActiveContractCount());
        result.put("pendingContractCount", dashboard.getPendingContractCount());
        result.put("productCount", dashboard.getProductCount());
        result.put("stockBatchCount", dashboard.getStockBatchCount());
        result.put("totalStockQuantity", dashboard.getTotalStockQuantity());
        result.put("pendingInboundReceiptCount", dashboard.getPendingInboundReceiptCount());
        result.put("pendingOutboundReceiptCount", dashboard.getPendingOutboundReceiptCount());
        result.put("pendingAuditCount", dashboard.getPendingAuditCount());
        result.put("pendingTransferCount", dashboard.getPendingTransferCount());
        result.put("activeStaffCount", dashboard.getActiveStaffCount());
        result.put("unreadNotificationCount", dashboard.getUnreadNotificationCount());

        TenantSubscriptionSummaryResponse subscription = dashboard.getActiveSubscription();
        if (subscription == null) {
            result.put("activeSubscription", null);
        } else {
            Map<String, Object> subscriptionMap = new LinkedHashMap<>();
            subscriptionMap.put("packageName", subscription.getPackageName());
            subscriptionMap.put("startDate", subscription.getStartDate());
            subscriptionMap.put("endDate", subscription.getEndDate());
            subscriptionMap.put("status", ChatToolLocalization.subscriptionStatus(subscription.getStatus()));
            result.put("activeSubscription", subscriptionMap);
        }
        return result;
    }
}
