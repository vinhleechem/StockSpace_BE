package fu.stockspace.stockspace_be.wms.product.controller;

import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.wms.product.dto.*;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.product.service.ProductCategoryService;
import fu.stockspace.stockspace_be.wms.product.service.ProductSkuService;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Tenant — WMS Tenant Product Management", description = "Các API WMS quản lý Danh mục & SKU dành cho Tenant & Staff")
@RestController
@RequestMapping("/api/tenant/products")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT', 'STAFF')")
public class TenantProductController {

    private final ProductCategoryService categoryService;
    private final ProductSkuService skuService;
    private final SubscriptionService subscriptionService;
    private final UnitOfMeasureRepository uomRepository;

    private UUID getCurrentTenantId() {
        return TenantContextUtil.getCurrentTenantId();
    }

    private void checkSubscription(UUID tenantId) {
        if (!subscriptionService.hasActiveSubscription(tenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }

    // ==================== Category APIs ====================

    @GetMapping("/categories")
    @Operation(summary = "Lấy danh sách danh mục sản phẩm (bao gồm danh mục đề xuất)")
    public ResponseEntity<ApiResponse<List<ProductCategoryResponse>>> getMyCategories() {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        List<ProductCategoryResponse> response = categoryService.getMyCategories(tenantId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục thành công", response));
    }

    @PostMapping("/categories")
    @Operation(summary = "Tạo danh mục sản phẩm mới")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        ProductCategoryResponse response = categoryService.createCategory(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo danh mục thành công", response));
    }

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "Xóa mềm danh mục sản phẩm (chỉ khi không có SKU liên kết)")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        categoryService.deleteCategory(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa danh mục thành công", null));
    }

    // ==================== SKU APIs ====================

    @GetMapping("/skus")
    @Operation(summary = "Lấy danh sách SKU sản phẩm phân trang (bao gồm SKU đề xuất)")
    public ResponseEntity<ApiResponse<PagedSkuResponse>> getMySKUs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        Pageable pageable = PageRequest.of(page, size);
        PagedSkuResponse response = skuService.getMySKUs(tenantId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách SKU thành công", response));
    }

    @GetMapping("/skus/{id}")
    @Operation(summary = "Xem chi tiết SKU sản phẩm")
    public ResponseEntity<ApiResponse<ProductSkuResponse>> getSkuDetail(@PathVariable UUID id) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        ProductSkuResponse response = skuService.getSkuDetail(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết SKU thành công", response));
    }

    @PostMapping("/skus")
    @Operation(summary = "Tạo SKU sản phẩm mới")
    public ResponseEntity<ApiResponse<ProductSkuResponse>> createSku(
            @Valid @RequestBody CreateSkuRequest request) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        ProductSkuResponse response = skuService.createSku(tenantId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo SKU thành công", response));
    }

    @PutMapping("/skus/{id}")
    @Operation(summary = "Cập nhật SKU sản phẩm")
    public ResponseEntity<ApiResponse<ProductSkuResponse>> updateSku(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSkuRequest request) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        ProductSkuResponse response = skuService.updateSku(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật SKU thành công", response));
    }

    @DeleteMapping("/skus/{id}")
    @Operation(summary = "Xóa mềm SKU sản phẩm (chỉ khi không có lô hàng StockBatch liên kết)")
    public ResponseEntity<ApiResponse<Void>> deleteSku(@PathVariable UUID id) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        skuService.deleteSku(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Xóa SKU thành công", null));
    }

    // ==================== UOMs ====================

    /**
     * Lấy danh sách các Đơn vị tính (UOM) hệ thống + của riêng Tenant
     */
    @GetMapping("/uoms")
    @Operation(summary = "Lấy danh sách Đơn vị tính (UOM)")
    public ResponseEntity<ApiResponse<PagedResponse<UnitOfMeasureResponse>>> getUoms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = getCurrentTenantId();
        checkSubscription(tenantId);
        Pageable pageable = PageRequest.of(page, size);
        Page<UnitOfMeasure> uomPage = uomRepository.findAllActiveByTenantOrSystem(tenantId, pageable);
        PagedResponse<UnitOfMeasureResponse> response = PagedResponse.fromPage(uomPage, uom -> UnitOfMeasureResponse.builder()
                .id(uom.getId())
                .name(uom.getName())
                .code(uom.getCode())
                .description(uom.getDescription())
                .build());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Đơn vị tính thành công", response));
    }
}
