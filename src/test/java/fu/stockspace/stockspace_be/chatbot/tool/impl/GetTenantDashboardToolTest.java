package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.stats.dto.TenantDashboardResponse;
import fu.stockspace.stockspace_be.stats.dto.TenantSubscriptionSummaryResponse;
import fu.stockspace.stockspace_be.stats.service.TenantDashboardService;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTenantDashboardToolTest {

    @Mock
    private TenantDashboardService dashboardService;

    @Test
    void returnsTenantSummaryWithoutInternalSubscriptionId() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        UUID userId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        when(dashboardService.getDashboard(userId)).thenReturn(TenantDashboardResponse.builder()
                .activeWarehouseCount(2)
                .activeContractCount(2)
                .pendingTransferCount(1)
                .unreadNotificationCount(3)
                .activeSubscription(TenantSubscriptionSummaryResponse.builder()
                        .id(subscriptionId)
                        .packageName("WMS Pro")
                        .startDate(LocalDate.of(2026, 9, 1))
                        .endDate(LocalDate.of(2026, 10, 1))
                        .status(SubscriptionStatus.ACTIVE)
                        .build())
                .build());

        String json = new GetTenantDashboardTool(objectMapper, dashboardService)
                .execute(java.util.Map.of(), userId);
        JsonNode result = objectMapper.readTree(json);

        assertEquals(2, result.get("activeWarehouseCount").asInt());
        assertEquals(1, result.get("pendingTransferCount").asInt());
        assertEquals("Đang hoạt động", result.at("/activeSubscription/status").asText());
        assertFalse(json.contains(subscriptionId.toString()));
    }
}
