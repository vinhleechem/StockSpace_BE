package fu.stockspace.stockspace_be.stats.controller;

import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.stats.dto.TenantDashboardResponse;
import fu.stockspace.stockspace_be.stats.service.TenantDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tenant Dashboard", description = "Tenant dashboard metrics")
@PreAuthorize("@rbac.hasPermission('TENANT_DASHBOARD_READ')")
public class TenantDashboardController {

    private final TenantDashboardService tenantDashboardService;

    @GetMapping
    @Operation(summary = "Get the current tenant dashboard")
    public ResponseEntity<ApiResponse<TenantDashboardResponse>> getDashboard() {
        TenantDashboardResponse response = tenantDashboardService.getDashboard(
                TenantContextUtil.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success("Tenant dashboard loaded", response));
    }
}
