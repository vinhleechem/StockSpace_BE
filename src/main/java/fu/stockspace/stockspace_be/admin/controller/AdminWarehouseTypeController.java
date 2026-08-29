package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.warehouse.dto.CreateWarehouseTypeRequest;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseTypeResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;







@Tag(name = "Admin — Warehouse Type Management", description = "Các API quản lý Loại kho của Admin")
@RestController
@RequestMapping("/api/admin/warehouse-types")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('ADMIN_WAREHOUSE_TYPE_MANAGE')")
public class AdminWarehouseTypeController {

    private final WarehouseTypeService warehouseTypeService;





    @GetMapping
    @Operation(summary = "Lấy danh sách loại kho (phân trang, tìm kiếm)")
    public ResponseEntity<ApiResponse<PagedResponse<WarehouseTypeResponse>>> getTypes(
            @Parameter(description = "Từ khóa tìm kiếm (tên / mô tả loại kho)")
            @RequestParam(required = false) String keyword,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        PagedResponse<WarehouseTypeResponse> result = warehouseTypeService.getTypesPaged(
                keyword, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách loại kho thành công", result));
    }






    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết loại kho theo ID")
    public ResponseEntity<ApiResponse<WarehouseTypeResponse>> getTypeById(
            @PathVariable java.util.UUID id
    ) {
        WarehouseTypeResponse type = warehouseTypeService.getTypeById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin loại kho thành công", type));
    }





    @PostMapping
    @Operation(summary = "Tạo mới một loại kho")
    public ResponseEntity<ApiResponse<WarehouseTypeResponse>> createType(
            @Valid @RequestBody CreateWarehouseTypeRequest request
    ) {
        WarehouseTypeResponse type = warehouseTypeService.createType(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo loại kho thành công", type));
    }





    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật loại kho")
    public ResponseEntity<ApiResponse<WarehouseTypeResponse>> updateType(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody CreateWarehouseTypeRequest request
    ) {
        WarehouseTypeResponse type = warehouseTypeService.updateType(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loại kho thành công", type));
    }





    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa loại kho")
    public ResponseEntity<ApiResponse<Void>> deleteType(
            @PathVariable java.util.UUID id
    ) {
        warehouseTypeService.deleteType(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa loại kho thành công", null));
    }
}
