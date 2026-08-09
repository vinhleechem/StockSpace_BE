package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller xử lý các API Warehouse cho Tenant & Staff.
 * Base path: /api/tenant/warehouses
 */
@Tag(name = "Tenant — Warehouse Management", description = "Các API kho bãi cho Tenant & Staff")
@RestController
@RequestMapping("/api/tenant/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT', 'STAFF')")
public class TenantWarehouseController {

    private final WarehouseService warehouseService;

    /**
     * GET /api/tenant/warehouses/my-warehouses
     * Lấy danh sách các kho mà Tenant/Staff đang có hợp đồng thuê hoạt động (ACTIVE).
     */
    @GetMapping("/my-warehouses")
    @Operation(summary = "Lấy danh sách các kho đang thuê của Tenant / Staff")
    public ResponseEntity<ApiResponse<List<WarehouseResponse>>> getMyRentedWarehouses() {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        List<WarehouseResponse> response = warehouseService.getActiveRentedWarehouses(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho đang thuê thành công", response));
    }
}
