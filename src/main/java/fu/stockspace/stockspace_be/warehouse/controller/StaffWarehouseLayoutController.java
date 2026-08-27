package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
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

@Tag(name = "Staff — Warehouse Layout", description = "Read-only layout and location lookup for assigned warehouses")
@RestController
@RequestMapping("/api/staff/warehouses")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
public class StaffWarehouseLayoutController {

    private final WarehouseLayoutService layoutService;

    @GetMapping("/{warehouseId}/layout")
    @Operation(summary = "Xem layout và vị trí lưu trữ của kho được phân công")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> getLayout(
            @PathVariable UUID warehouseId) {
        User staff = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
        WarehouseLayoutResponse response = layoutService.getStaffLayoutTree(
                warehouseId, staff.getId(), TenantContextUtil.getCurrentTenantId());
        return ResponseEntity.ok(ApiResponse.success("Lấy layout kho được phân công thành công", response));
    }
}
