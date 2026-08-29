package fu.stockspace.stockspace_be.wms.capacity.controller;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.WarehouseLayoutCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.service.WarehouseCapacityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Tenant — WMS Capacity Metrics", description = "Read-only physical load metrics for a tenant warehouse layout")
@RestController
@RequestMapping("/api/tenant/warehouses")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
public class WarehouseCapacityController {

    private final WarehouseCapacityService capacityService;

    @GetMapping("/{warehouseId}/layout/capacity")
    @Operation(summary = "Get rack and bin capacity metrics for a warehouse")
    public ResponseEntity<ApiResponse<WarehouseLayoutCapacityResponse>> getCapacity(
            @PathVariable UUID warehouseId) {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        WarehouseLayoutCapacityResponse response = capacityService.getCapacity(
                tenantId, warehouseId, getCurrentStaffIdIfApplicable());
        return ResponseEntity.ok(ApiResponse.success("Capacity metrics loaded successfully", response));
    }

    private UUID getCurrentStaffIdIfApplicable() {
        return SecurityUtil.getCurrentUser()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName())))
                .map(user -> user.getId())
                .orElse(null);
    }
}
