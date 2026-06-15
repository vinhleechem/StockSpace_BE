package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;


/**
 * Controller xử lý các API công khai cho Warehouse — không yêu cầu xác thực.
 *
 * Endpoints:
 *   GET /api/warehouses         — Tìm kiếm & lọc kho (chỉ kho đã verified)
 *   GET /api/warehouses/{id}    — Xem chi tiết kho
 *   GET /api/warehouses/types   — Danh sách loại kho
 */
@Tag(name = "Public — Warehouse", description = "API công khai tìm kiếm và xem kho bãi")
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class PublicWarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseTypeService warehouseTypeService;

    // ==================== Search ====================

    /**
     * GET /api/warehouses
     * Tìm kiếm kho đã được xác minh (isVerified = true).
     *
     * Query params:
     *   - keyword    : tìm theo tên / địa chỉ
     *   - minPrice   : giá thuê tối thiểu (VNĐ/tháng)
     *   - maxPrice   : giá thuê tối đa
     *   - minCapacity: sức chứa tối thiểu (m²)
     *   - page, size : phân trang
     *   - sortBy     : trường sắp xếp (mặc định: createdAt)
     *   - sortDir    : asc / desc
     */
    @GetMapping
    @Operation(summary = "Tìm kiếm kho bãi công khai (đã xác minh)")
    public ResponseEntity<ApiResponse<PagedWarehouseResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minCapacity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        WarehouseSearchRequest request = new WarehouseSearchRequest();
        request.setKeyword(keyword);
        request.setMinPrice(minPrice);
        request.setMaxPrice(maxPrice);
        request.setMinCapacity(minCapacity);

        PagedWarehouseResponse result = warehouseService.searchWarehouses(request, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công", result));
    }

    // ==================== Detail ====================

    /**
     * GET /api/warehouses/{id}
     * Xem chi tiết một Warehouse (chỉ kho đã verified).
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết kho bãi")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getDetail(@PathVariable Long id) {
        WarehouseResponse response = warehouseService.getWarehouseDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin kho thành công", response));
    }

    // ==================== Types ====================

    /**
     * GET /api/warehouses/types
     * Lấy danh sách toàn bộ loại kho (không phân trang).
     */
    @GetMapping("/types")
    @Operation(summary = "Lấy danh sách tất cả loại kho")
    public ResponseEntity<ApiResponse<List<WarehouseTypeResponse>>> getAllTypes() {
        List<WarehouseTypeResponse> types = warehouseTypeService.getAllTypes();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách loại kho thành công", types));
    }
}
