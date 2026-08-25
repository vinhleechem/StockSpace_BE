package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;










@Tag(name = "Public — Warehouse", description = "API công khai tìm kiếm và xem kho bãi")
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class PublicWarehouseController {

    private static final int MAX_SEARCH_PAGE = 10_000;
    private static final int MAX_SEARCH_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final BigDecimal MAX_FILTER_AMOUNT = new BigDecimal("9999999999999.99");
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "updatedAt", "name", "pricePerMonth", "rentalPrice", "capacity"
    );

    private final WarehouseService warehouseService;
    private final WarehouseTypeService warehouseTypeService;
    private final WarehouseLayoutService warehouseLayoutService;
















    @GetMapping
    @Operation(summary = "Tìm kiếm kho bãi công khai")
    public ResponseEntity<ApiResponse<PagedResponse<WarehouseResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRentalPrice,
            @RequestParam(required = false) BigDecimal maxRentalPrice,
            @RequestParam(required = false) BigDecimal minCapacity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        BigDecimal effectiveMinRentalPrice = coalescePriceFilter("minPrice", minPrice,
                "minRentalPrice", minRentalPrice);
        BigDecimal effectiveMaxRentalPrice = coalescePriceFilter("maxPrice", maxPrice,
                "maxRentalPrice", maxRentalPrice);
        validateSearchParameters(keyword, effectiveMinRentalPrice, effectiveMaxRentalPrice,
                minCapacity, page, size, sortBy, sortDir);

        WarehouseSearchRequest request = new WarehouseSearchRequest();
        request.setKeyword(keyword);
        request.setMinRentalPrice(effectiveMinRentalPrice);
        request.setMaxRentalPrice(effectiveMaxRentalPrice);
        request.setMinCapacity(minCapacity);

        PagedResponse<WarehouseResponse> result = warehouseService.searchWarehouses(request, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kho thành công", result));
    }







    private void validateSearchParameters(
            String keyword,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minCapacity,
            int page,
            int size,
            String sortBy,
            String sortDir
    ) {
        if (page < 0 || page > MAX_SEARCH_PAGE) {
            throw new BadRequestException("Page must be between 0 and " + MAX_SEARCH_PAGE);
        }
        if (size < 1 || size > MAX_SEARCH_SIZE) {
            throw new BadRequestException("Size must be between 1 and " + MAX_SEARCH_SIZE);
        }
        if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BadRequestException("Keyword must not exceed " + MAX_KEYWORD_LENGTH + " characters");
        }
        validateFilterAmount("minPrice", minPrice);
        validateFilterAmount("maxPrice", maxPrice);
        validateFilterAmount("minCapacity", minCapacity);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException("Unsupported sortBy value");
        }
        if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
            throw new BadRequestException("sortDir must be asc or desc");
        }
    }

    private void validateFilterAmount(String field, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.signum() < 0 || value.compareTo(MAX_FILTER_AMOUNT) > 0 || value.scale() > 2) {
            throw new BadRequestException(field + " must be between 0 and "
                    + MAX_FILTER_AMOUNT.toPlainString() + " with at most 2 decimal places");
        }
    }

    private BigDecimal coalescePriceFilter(String legacyName, BigDecimal legacyValue,
                                           String currentName, BigDecimal currentValue) {
        if (legacyValue != null && currentValue != null
                && legacyValue.compareTo(currentValue) != 0) {
            throw new BadRequestException(legacyName + " and " + currentName + " must match when both are provided");
        }
        return currentValue != null ? currentValue : legacyValue;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết kho bãi")
    public ResponseEntity<ApiResponse<WarehouseResponse>> getDetail(@PathVariable UUID id) {
        WarehouseResponse response = warehouseService.getWarehouseDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin kho thành công", response));
    }

    @GetMapping("/{id}/owner-contact")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get the warehouse owner contact for an authenticated user")
    public ResponseEntity<ApiResponse<WarehouseOwnerContactResponse>> getOwnerContact(
            @PathVariable UUID id) {
        WarehouseOwnerContactResponse response = warehouseService.getOwnerContact(id);
        return ResponseEntity.ok(ApiResponse.success("Owner contact loaded", response));
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
