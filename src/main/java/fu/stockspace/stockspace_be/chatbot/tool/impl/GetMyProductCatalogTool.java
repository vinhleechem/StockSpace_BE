package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.product.dto.ProductCategoryResponse;
import fu.stockspace.stockspace_be.wms.product.dto.ProductSkuResponse;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.product.service.ProductCategoryService;
import fu.stockspace.stockspace_be.wms.product.service.ProductSkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyProductCatalogTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final ProductSkuService skuService;
    private final ProductCategoryService categoryService;
    private final UnitOfMeasureRepository unitRepository;

    @Override
    public String getName() {
        return "getMyProductCatalog";
    }

    @Override
    public String getDescription() {
        return "Xem danh mục hàng hóa của người thuê: SKU, nhóm sản phẩm hoặc đơn vị tính; có thể xem chi tiết một SKU. "
                + "Đây là dữ liệu danh mục, khác với số lượng tồn kho thực tế.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "view", Map.of("type", "string", "enum", List.of("SKUS", "CATEGORIES", "UNITS")),
                        "skuId", Map.of("type", "string", "description", "Mã SKU nội bộ nếu cần xem chi tiết"),
                        "page", Map.of("type", "integer", "minimum", 0),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem danh mục hàng hóa.\"}";
        }
        try {
            String view = params == null || params.get("view") == null
                    ? "SKUS" : params.get("view").toString().trim().toUpperCase(Locale.ROOT);
            return switch (view) {
                case "SKUS" -> readSkus(params, userId);
                case "CATEGORIES" -> objectMapper.writeValueAsString(Map.of(
                        "categories", categoryService.getMyCategories(userId).stream()
                                .map(this::category).toList()));
                case "UNITS" -> readUnits(params, userId);
                default -> throw new IllegalArgumentException("Unsupported view");
            };
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Tham số danh mục hàng hóa không hợp lệ.\"}";
        } catch (Exception exception) {
            log.warn("[GetMyProductCatalogTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh mục hàng hóa lúc này.\"}";
        }
    }

    private String readSkus(Map<String, Object> params, UUID userId) throws Exception {
        UUID skuId = ChatToolParameters.optionalUuid(params, "skuId");
        if (skuId != null) {
            return objectMapper.writeValueAsString(Map.of(
                    "sku", sku(skuService.getSkuDetail(userId, skuId), true)));
        }
        int pageNumber = ChatToolParameters.page(params);
        int pageSize = ChatToolParameters.pageSize(params, 15, 30);
        PagedResponse<ProductSkuResponse> page = skuService.getMySKUs(
                userId, PageRequest.of(pageNumber, pageSize, Sort.by("name").ascending()));
        Map<String, Object> result = pageMetadata(page.getPage(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
        result.put("skus", page.getContent().stream().map(value -> sku(value, false)).toList());
        return objectMapper.writeValueAsString(result);
    }

    private String readUnits(Map<String, Object> params, UUID userId) throws Exception {
        int pageNumber = ChatToolParameters.page(params);
        int pageSize = ChatToolParameters.pageSize(params, 20, 30);
        Page<UnitOfMeasure> page = unitRepository.findAllActiveByTenantOrSystem(
                userId, PageRequest.of(pageNumber, pageSize, Sort.by("name").ascending()));
        Map<String, Object> result = pageMetadata(page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
        result.put("units", page.getContent().stream().map(this::unit).toList());
        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> pageMetadata(int page, int size, long total, int totalPages, boolean last) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("page", page);
        result.put("pageSize", size);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("hasMore", !last);
        return result;
    }

    private Map<String, Object> sku(ProductSkuResponse sku, boolean includeSpecifications) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sku.getId());
        result.put("code", sku.getSkuCode());
        result.put("name", sku.getName());
        result.put("category", sku.getCategoryName());
        result.put("unitCode", sku.getUomCode());
        result.put("unitName", sku.getUomName());
        result.put("unitWeightKg", sku.getUnitWeightKg());
        result.put("unitVolumeM3", sku.getUnitVolumeM3());
        if (includeSpecifications && sku.getSpecifications() != null) {
            try {
                String serialized = objectMapper.writeValueAsString(sku.getSpecifications());
                if (serialized.length() <= 4_000) {
                    result.put("specifications", sku.getSpecifications());
                } else {
                    result.put("specificationsSummary", serialized.substring(0, 4_000));
                    result.put("specificationsTruncated", true);
                }
            } catch (Exception ignored) {
                result.put("specificationsUnavailable", true);
            }
        }
        return result;
    }

    private Map<String, Object> category(ProductCategoryResponse category) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", category.getName());
        result.put("defaultAttributes", category.getDefaultAttributes());
        return result;
    }

    private Map<String, Object> unit(UnitOfMeasure unit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", unit.getCode());
        result.put("name", unit.getName());
        result.put("description", unit.getDescription());
        return result;
    }
}
