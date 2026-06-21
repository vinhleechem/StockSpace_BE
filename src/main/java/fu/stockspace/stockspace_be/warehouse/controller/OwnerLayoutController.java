package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.warehouse.dto.BulkLayoutSaveRequest;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Owner — Layout Management", description = "Quản lý sơ đồ Layout kho mặc định của Owner")
@RestController
@RequestMapping("/api/owner/warehouses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class OwnerLayoutController {

    private final WarehouseLayoutService layoutService;

    @GetMapping("/{warehouseId}/layout")
    @Operation(summary = "Lấy sơ đồ layout mặc định (Owner)")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> getLayout(@PathVariable UUID warehouseId) {
        UUID userId = getCurrentUserId();
        WarehouseLayoutResponse response = layoutService.getLayoutTree(warehouseId, userId, "OWNER");
        return ResponseEntity.ok(ApiResponse.success("Lấy sơ đồ layout mặc định thành công", response));
    }

    @PutMapping("/{warehouseId}/layout")
    @Operation(summary = "Lưu/cập nhật hàng loạt sơ đồ layout mặc định (Owner)")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> saveLayout(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody BulkLayoutSaveRequest request
    ) {
        UUID userId = getCurrentUserId();
        WarehouseLayoutResponse response = layoutService.saveLayoutBulk(warehouseId, userId, "OWNER", request);
        return ResponseEntity.ok(ApiResponse.success("Lưu sơ đồ layout mặc định thành công", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
