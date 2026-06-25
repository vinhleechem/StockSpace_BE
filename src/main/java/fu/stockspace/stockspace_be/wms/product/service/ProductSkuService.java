package fu.stockspace.stockspace_be.wms.product.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wms.product.dto.CreateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.dto.PagedSkuResponse;
import fu.stockspace.stockspace_be.wms.product.dto.ProductSkuResponse;
import fu.stockspace.stockspace_be.wms.product.dto.UpdateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSkuService {

    private final ProductSkuRepository skuRepository;
    private final ProductCategoryRepository categoryRepository;
    private final StockBatchRepository stockBatchRepository;
    private final UserRepository userRepository;

    public PagedSkuResponse getMySKUs(UUID tenantId, Pageable pageable) {
        Page<ProductSku> page = skuRepository.findAllActiveByTenantOrSystem(tenantId, pageable);
        List<ProductSkuResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PagedSkuResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    public ProductSkuResponse getSkuDetail(UUID tenantId, UUID skuId) {
        ProductSku sku = skuRepository.findByIdAndIsDeletedFalse(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        // Must be visible to the tenant
        if (sku.getTenant() != null && !sku.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        return mapToResponse(sku);
    }

    @Transactional
    public ProductSkuResponse createSku(UUID tenantId, CreateSkuRequest request) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        ProductCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndIsDeletedFalse(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));

            // Category must be visible to the tenant
            if (category.getTenant() != null && !category.getTenant().getId().equals(tenantId)) {
                throw new BadRequestException(ErrorCode.FORBIDDEN);
            }
        }

        // Validate uniqueness of skuCode per tenant
        if (skuRepository.existsBySkuCodeAndTenantOrSystem(request.getSkuCode(), tenantId)) {
            throw new BadRequestException(ErrorCode.SKU_CODE_DUPLICATE);
        }

        ProductSku sku = ProductSku.builder()
                .tenant(tenant)
                .category(category)
                .skuCode(request.getSkuCode())
                .name(request.getName())
                .unit(request.getUnit())
                .specifications(request.getSpecifications())
                .build();

        ProductSku saved = skuRepository.save(sku);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductSkuResponse updateSku(UUID tenantId, UUID skuId, UpdateSkuRequest request) {
        ProductSku sku = skuRepository.findByIdAndIsDeletedFalse(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        // System SKUs cannot be updated by tenants
        if (sku.getTenant() == null) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        // Must own the SKU
        if (!sku.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        ProductCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndIsDeletedFalse(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));

            // Category must be visible to the tenant
            if (category.getTenant() != null && !category.getTenant().getId().equals(tenantId)) {
                throw new BadRequestException(ErrorCode.FORBIDDEN);
            }
        }

        sku.setCategory(category);
        sku.setName(request.getName());
        sku.setUnit(request.getUnit());
        sku.setSpecifications(request.getSpecifications());

        ProductSku saved = skuRepository.save(sku);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteSku(UUID tenantId, UUID skuId) {
        ProductSku sku = skuRepository.findByIdAndIsDeletedFalse(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));

        // System SKUs cannot be deleted by tenants
        if (sku.getTenant() == null) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        // Must own the SKU
        if (!sku.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        // Check constraint: Block deletion if any StockBatch is linked to this SKU (even if quantity is 0)
        if (stockBatchRepository.existsBySkuIdAndIsDeletedFalse(skuId)) {
            throw new BadRequestException(ErrorCode.SKU_IN_USE);
        }

        sku.setDeleted(true);
        sku.setActive(false);
        skuRepository.save(sku);
    }

    private ProductSkuResponse mapToResponse(ProductSku sku) {
        return ProductSkuResponse.builder()
                .id(sku.getId())
                .tenantId(sku.getTenant() != null ? sku.getTenant().getId() : null)
                .categoryId(sku.getCategory() != null ? sku.getCategory().getId() : null)
                .categoryName(sku.getCategory() != null ? sku.getCategory().getName() : null)
                .skuCode(sku.getSkuCode())
                .name(sku.getName())
                .unit(sku.getUnit())
                .specifications(sku.getSpecifications())
                .build();
    }
}
