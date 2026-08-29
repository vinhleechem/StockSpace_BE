package fu.stockspace.stockspace_be.wms.product.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.wms.product.dto.CreateCategoryRequest;
import fu.stockspace.stockspace_be.wms.product.dto.ProductCategoryResponse;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductSkuRepository skuRepository;
    private final UserRepository userRepository;
    private final TenantWarehouseAccessService accessService;

    public List<ProductCategoryResponse> getMyCategories(UUID tenantId) {
        List<ProductCategory> categories = categoryRepository.findAllActiveByTenantOrSystem(tenantId);
        return categories.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductCategoryResponse createCategory(UUID tenantId, CreateCategoryRequest request) {
        accessService.requireActiveSubscription(tenantId);
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        ProductCategory category = ProductCategory.builder()
                .tenant(tenant)
                .name(request.getName())
                .defaultAttributes(request.getDefaultAttributes())
                .build();

        ProductCategory saved = categoryRepository.save(category);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteCategory(UUID tenantId, UUID categoryId) {
        accessService.requireActiveSubscription(tenantId);
        ProductCategory category = categoryRepository.findByIdAndIsDeletedFalse(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));


        if (category.getTenant() == null) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }


        if (!category.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }


        if (skuRepository.existsByCategoryIdAndIsDeletedFalse(categoryId)) {
            throw new BadRequestException(ErrorCode.PRODUCT_CATEGORY_IN_USE);
        }

        category.setDeleted(true);
        category.setActive(false);
        categoryRepository.save(category);
    }

    private ProductCategoryResponse mapToResponse(ProductCategory category) {
        return ProductCategoryResponse.builder()
                .id(category.getId())
                .tenantId(category.getTenant() != null ? category.getTenant().getId() : null)
                .name(category.getName())
                .defaultAttributes(category.getDefaultAttributes())
                .build();
    }
}
