package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.warehouse.dto.*;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseTypeService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;










@Tag(name = "Public — Warehouse", description = "API công khai tìm kiếm và xem kho bãi")
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class PublicWarehouseController {

    private final WarehouseService warehouseService;
    private final WarehouseTypeService warehouseTypeService;
    private final WarehouseLayoutService warehouseLayoutService;
















    @GetMapping
    @Operation(summary = "Tìm kiếm kho bãi công khai")
    public ResponseEntity<ApiResponse<PagedResponse<WarehouseResponse>>> search(
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

        PagedResponse<WarehouseResponse> result = warehouseService.searchWarehouses(request, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công", result));
    }







    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết kho bãi")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getDetail(@PathVariable UUID id) {
        WarehouseResponse response = warehouseService.getWarehouseDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin kho thành công", response));
    }







    @GetMapping("/{id}/layout")
    @Operation(summary = "Lấy sơ đồ layout kho bãi (Public / Guest / Tenant)")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> getLayout(@PathVariable UUID id) {
        UUID userId = null;
        String role = "PUBLIC";

        var currentUserOpt = SecurityUtil.getCurrentUser();
        if (currentUserOpt.isPresent()) {
            User user = currentUserOpt.get();
            userId = user.getId();
            boolean isTenant = user.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_TENANT"));
            boolean isOwner = user.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER") || a.getAuthority().equals("ROLE_ADMIN"));
            if (isTenant) {
                role = "TENANT";
            } else if (isOwner) {
                role = "OWNER";
            }
        }

        WarehouseLayoutResponse response = warehouseLayoutService.getLayoutTree(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success("Lấy sơ đồ layout kho thành công", response));
    }







    @GetMapping("/types")
    @Operation(summary = "Lấy danh sách tất cả loại kho")
    public ResponseEntity<ApiResponse<List<WarehouseTypeResponse>>> getAllTypes() {
        List<WarehouseTypeResponse> types = warehouseTypeService.getAllTypes();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách loại kho thành công", types));
    }
}
