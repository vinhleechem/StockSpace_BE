package fu.stockspace.stockspace_be.wms.product.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wms.product.dto.CreateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.dto.ProductSkuResponse;
import fu.stockspace.stockspace_be.wms.product.dto.UpdateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductSkuService {

    private final ProductSkuRepository skuRepository;
    private final ProductCategoryRepository categoryRepository;
    private final StockBatchRepository stockBatchRepository;
    private final UserRepository userRepository;
    private final UnitOfMeasureRepository uomRepository;

    public PagedResponse<ProductSkuResponse> getMySKUs(UUID tenantId, Pageable pageable) {
        Page<ProductSku> page = skuRepository.findAllActiveByTenantOrSystem(tenantId, pageable);
        return PagedResponse.fromPage(page, this::mapToResponse);
    }

    public ProductSkuResponse getSkuDetail(UUID tenantId, UUID skuId) {
        ProductSku sku = skuRepository.findByIdAndIsDeletedFalse(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));


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


            if (category.getTenant() != null && !category.getTenant().getId().equals(tenantId)) {
                throw new BadRequestException(ErrorCode.FORBIDDEN);
            }
        }

        UnitOfMeasure uom = uomRepository.findByIdAndIsDeletedFalse(request.getUomId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.UOM_NOT_FOUND));


        if (uom.getTenant() != null && !uom.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }


        if (skuRepository.existsBySkuCodeAndTenantOrSystem(request.getSkuCode(), tenantId)) {
            throw new BadRequestException(ErrorCode.SKU_CODE_DUPLICATE);
        }

        validatePhysicalProperties(request.getUnitWeightKg(), request.getUnitVolumeM3());

        ProductSku sku = ProductSku.builder()
                .tenant(tenant)
                .category(category)
                .skuCode(request.getSkuCode())
                .name(request.getName())
                .uom(uom)
                .unitWeightKg(request.getUnitWeightKg())
                .unitVolumeM3(request.getUnitVolumeM3())
                .specifications(request.getSpecifications())
                .build();

        ProductSku saved = skuRepository.save(sku);
        return mapToResponse(saved);
    }

    @Transactional
    public ProductSkuResponse updateSku(UUID tenantId, UUID skuId, UpdateSkuRequest request) {
        ProductSku sku = skuRepository.findByIdAndIsDeletedFalse(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));


        if (sku.getTenant() == null) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }


        if (!sku.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        validatePhysicalProperties(request.getUnitWeightKg(), request.getUnitVolumeM3());

        ProductCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndIsDeletedFalse(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_CATEGORY_NOT_FOUND));


            if (category.getTenant() != null && !category.getTenant().getId().equals(tenantId)) {
                throw new BadRequestException(ErrorCode.FORBIDDEN);
            }
        }

        UnitOfMeasure uom = uomRepository.findByIdAndIsDeletedFalse(request.getUomId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.UOM_NOT_FOUND));


        if (uom.getTenant() != null && !uom.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }

        sku.setCategory(category);
        sku.setName(request.getName());
        sku.setUom(uom);
        if (stockBatchRepository.existsBySkuIdAndIsDeletedFalse(skuId)
                && (!request.getUnitWeightKg().equals(sku.getUnitWeightKg())
                || !request.getUnitVolumeM3().equals(sku.getUnitVolumeM3()))) {
            throw new BadRequestException("Physical properties cannot be changed after stock has been recorded");
        }
        sku.setUnitWeightKg(request.getUnitWeightKg());
        sku.setUnitVolumeM3(request.getUnitVolumeM3());
        sku.setSpecifications(request.getSpecifications());

        ProductSku saved = skuRepository.save(sku);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteSku(UUID tenantId, UUID skuId) {
        ProductSku sku = skuRepository.findByIdAndIsDeletedFalse(skuId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SKU_NOT_FOUND));


        if (sku.getTenant() == null) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }


        if (!sku.getTenant().getId().equals(tenantId)) {
            throw new BadRequestException(ErrorCode.FORBIDDEN);
        }


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
                .uomId(sku.getUom() != null ? sku.getUom().getId() : null)
                .uomCode(sku.getUom() != null ? sku.getUom().getCode() : null)
                .uomName(sku.getUom() != null ? sku.getUom().getName() : null)
                .unitWeightKg(sku.getUnitWeightKg())
                .unitVolumeM3(sku.getUnitVolumeM3())
                .specifications(sku.getSpecifications())
                .build();
    }

    private void validatePhysicalProperties(BigDecimal unitWeightKg, BigDecimal unitVolumeM3) {
        if (unitWeightKg == null || unitWeightKg.signum() <= 0
                || unitVolumeM3 == null || unitVolumeM3.signum() <= 0) {
            throw new BadRequestException("unitWeightKg and unitVolumeM3 must both be greater than 0");
        }
    }
}
