package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseSearchRequest;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller xử lý các API quản lý và duyệt kho dành cho Admin và Inspector.
 */
@Tag(name = "Admin — Warehouse Management", description = "Các API duyệt và quản lý Kho của Admin/Inspector")
@RestController
@RequestMapping("/api/admin/warehouses")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('WAREHOUSE_REVIEW')")
public class AdminWarehouseController {

    private final WarehouseService warehouseService;

    /**
     * GET /api/admin/warehouses
     * Lấy danh sách toàn bộ các kho (không lọc verified) có phân trang, tìm kiếm và lọc.
     */
    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các kho (phân trang, tìm kiếm, lọc)")
    public ResponseEntity<ApiResponse<PagedResponse<WarehouseResponse>>> getAllWarehouses(
            @Parameter(description = "Từ khóa tìm kiếm (tên kho / địa chỉ)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Lọc theo trạng thái kho")
            @RequestParam(required = false) WarehouseStatus status,

            @Parameter(description = "Lọc theo trạng thái xác minh (true/false)")
            @RequestParam(required = false) Boolean isVerified,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        WarehouseSearchRequest request = new WarehouseSearchRequest();
        request.setKeyword(keyword);
        request.setStatus(status);
        request.setIsVerified(isVerified);

        PagedResponse<WarehouseResponse> result = warehouseService.getAllWarehouses(request, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công", result));
    }

    /**
     * POST /api/admin/warehouses/{id}/verify
     * Admin duyệt kho (xác minh thành công).
     */
    @PostMapping("/{id}/verify")
    @Operation(summary = "Duyệt kho (Xác minh thành công)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> verifyWarehouse(
            @PathVariable UUID id
    ) {
        WarehouseResponse response = warehouseService.verifyWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success("Duyệt kho thành công. Kho hiện đã sẵn sàng hoạt động.", response));
    }

    /**
     * POST /api/admin/warehouses/{id}/reject
     * Admin từ chối duyệt kho (thiết lập trạng thái INACTIVE).
     */
    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối duyệt kho")
    public ResponseEntity<ApiResponse<WarehouseResponse>> rejectWarehouse(
            @PathVariable UUID id
    ) {
        WarehouseResponse response = warehouseService.rejectWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success("Từ chối duyệt kho thành công", response));
    }
}
