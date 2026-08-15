package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.warehouse.dto.RejectWarehouseRequest;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseSearchRequest;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;




@Tag(name = "Admin — Warehouse Management", description = "Các API duyệt và quản lý Kho của Admin/Inspector")
@RestController
@RequestMapping("/api/admin/warehouses")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('WAREHOUSE_REVIEW')")
public class AdminWarehouseController {

    private final WarehouseService warehouseService;





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





    @PostMapping("/{id}/verify")
    @Operation(summary = "Duyệt kho (Xác minh thành công)")
    public ResponseEntity<ApiResponse<WarehouseResponse>> verifyWarehouse(
            @PathVariable UUID id
    ) {
        WarehouseResponse response = warehouseService.verifyWarehouse(id);
        return ResponseEntity.ok(ApiResponse.success("Duyệt kho thành công. Kho hiện đã sẵn sàng hoạt động.", response));
    }





    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối duyệt kho")
    public ResponseEntity<ApiResponse<WarehouseResponse>> rejectWarehouse(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RejectWarehouseRequest request
    ) {
        String reason = request != null ? request.getReason() : null;
        WarehouseResponse response = warehouseService.rejectWarehouse(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối duyệt kho thành công", response));
    }
}
