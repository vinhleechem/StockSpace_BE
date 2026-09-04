package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchWarehousesTool implements ChatTool {

    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_RESULT_LIMIT = 20;
    private static final int FALLBACK_RESULT_LIMIT = 8;

    private final WarehouseRepository warehouseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "searchWarehouses";
    }

    @Override
    public String getDescription() {
        return "Tìm các bài đăng kho còn hiệu lực theo từ khóa, giá niêm yết, sức chứa và trạng thái xác minh. "
                + "Từ khóa có thể là tên, địa chỉ, tỉnh/thành, quận/huyện, loại kho hoặc nhu cầu lưu trữ. "
                + "Giá niêm yết có thể là giá cố định theo tháng, giá mỗi m² mỗi tháng hoặc để thỏa thuận; "
                + "nếu người dùng không nêu tiêu chí, gọi với tham số rỗng để lấy các kho đang công khai.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description",
                "Tên, địa chỉ, tỉnh/thành, quận/huyện, loại kho hoặc loại hàng cần lưu trữ"));
        properties.put("minRentalPrice", Map.of("type", "number", "description", "Giá niêm yết tối thiểu"));
        properties.put("maxRentalPrice", Map.of("type", "number", "description", "Giá niêm yết tối đa"));
        properties.put("minCapacity", Map.of("type", "number", "description", "Sức chứa tối thiểu"));
        properties.put("maxCapacity", Map.of("type", "number", "description", "Sức chứa tối đa"));
        properties.put("isVerified", Map.of("type", "boolean", "description", "Chỉ lấy kho đã xác minh"));
        properties.put("page", Map.of("type", "integer", "minimum", 0));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", MAX_RESULT_LIMIT));
        return Map.of("type", "object", "properties", properties);
    }

    @Override
    @Transactional(readOnly = true)
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            Map<String, Object> safeParams = params == null ? Map.of() : params;
            String keyword = getStringParam(safeParams, "keyword");
            BigDecimal minPrice = getNonNegativeDecimalParam(safeParams, "minRentalPrice");
            BigDecimal maxPrice = getNonNegativeDecimalParam(safeParams, "maxRentalPrice");
            BigDecimal minCapacity = getNonNegativeDecimalParam(safeParams, "minCapacity");
            BigDecimal maxCapacity = getNonNegativeDecimalParam(safeParams, "maxCapacity");
            Boolean isVerified = getBooleanParam(safeParams, "isVerified");
            int page = ChatToolParameters.page(safeParams);
            int pageSize = ChatToolParameters.pageSize(safeParams, DEFAULT_RESULT_LIMIT, MAX_RESULT_LIMIT);
            validateRange(minPrice, maxPrice, "Giá niêm yết tối thiểu không được lớn hơn giá niêm yết tối đa");
            validateRange(minCapacity, maxCapacity, "Sức chứa tối thiểu không được lớn hơn sức chứa tối đa");

            Page<Warehouse> results = search(
                    keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%",
                    minPrice, maxPrice, minCapacity, maxCapacity, isVerified, page, pageSize);

            if (results.getTotalElements() == 0 && keyword != null
                    && keyword.toLowerCase(Locale.ROOT).startsWith("kho ")) {
                String strippedKeyword = keyword.substring(4).trim();
                if (!strippedKeyword.isBlank()) {
                    results = search("%" + strippedKeyword.toLowerCase(Locale.ROOT) + "%",
                            minPrice, maxPrice, minCapacity, maxCapacity, isVerified, page, pageSize);
                }
            }

            if (page == 0 && results.getTotalElements() == 0 && keyword != null) {
                Page<Warehouse> fallback = search(null, minPrice, maxPrice, minCapacity, maxCapacity,
                        isVerified, 0, Math.max(pageSize, FALLBACK_RESULT_LIMIT));
                if (!fallback.isEmpty()) {
                    Map<String, Object> response = baseResponse(fallback);
                    response.put("matchedByExactKeyword", false);
                    response.put("requestedKeyword", keyword);
                    response.put("guidance",
                            "Không có kết quả khớp chính xác; hãy so sánh mô tả, loại kho và vị trí trước khi gợi ý.");
                    return objectMapper.writeValueAsString(response);
                }
            }

            Map<String, Object> response = baseResponse(results);
            if (results.isEmpty()) {
                response.put("message", "Không tìm thấy bài đăng kho còn hiệu lực phù hợp với bộ lọc.");
            }
            return objectMapper.writeValueAsString(response);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.warn("[SearchWarehousesTool] Search failed", e);
            return error("Không thể tìm kiếm kho lúc này.");
        }
    }

    private Page<Warehouse> search(String keyword, BigDecimal minPrice, BigDecimal maxPrice,
                                   BigDecimal minCapacity, BigDecimal maxCapacity,
                                   Boolean isVerified, int page, int limit) {
        return warehouseRepository.searchPublic(
                keyword, WarehouseStatus.AVAILABLE, minPrice, maxPrice, minCapacity, maxCapacity,
                null, null, null, isVerified, PageRequest.of(page, limit));
    }

    private Map<String, Object> baseResponse(Page<Warehouse> page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotalElements());
        result.put("page", page.getNumber());
        result.put("pageSize", page.getSize());
        result.put("totalPages", page.getTotalPages());
        result.put("hasMore", !page.isLast());
        result.put("warehouses", page.getContent().stream().map(this::toMap).toList());
        return result;
    }

    private Map<String, Object> toMap(Warehouse warehouse) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", warehouse.getId());
        result.put("name", warehouse.getName());
        result.put("address", warehouse.getAddress());
        result.put("province", warehouse.getProvinceName());
        result.put("district", warehouse.getDistrictName());
        result.put("description", warehouse.getDescription());
        result.put("capacity", warehouse.getCapacity());
        result.put("pricingType", ChatToolLocalization.rentalPricingType(warehouse.getRentalPricingType()));
        result.put("listedRentalPrice", warehouse.getRentalPrice());
        result.put("priceUnit", priceUnit(warehouse.getRentalPricingType()));
        result.put("type", warehouse.getType() == null ? null : warehouse.getType().getName());
        result.put("verified", warehouse.isVerified());
        result.put("listingVisibleUntil", warehouse.getVisibleUntil());
        result.put("status", ChatToolLocalization.warehouseStatus(warehouse.getStatus()));
        return result;
    }

    private String priceUnit(RentalPricingType pricingType) {
        if (pricingType == null) {
            return null;
        }
        return switch (pricingType) {
            case FIXED_MONTHLY -> "VND/tháng";
            case PER_SQUARE_METER_MONTHLY -> "VND/m²/tháng";
            case NEGOTIATED -> "Thỏa thuận";
        };
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private BigDecimal getNonNegativeDecimalParam(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.toString());
            if (value.signum() < 0) {
                throw new IllegalArgumentException(ChatToolLocalization.filterLabel(key) + " không được là số âm");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(ChatToolLocalization.filterLabel(key) + " phải là một số hợp lệ");
        }
    }

    private Boolean getBooleanParam(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            return Boolean.valueOf(value);
        }
        throw new IllegalArgumentException("Trạng thái xác minh phải là true hoặc false");
    }

    private void validateRange(BigDecimal minimum, BigDecimal maximum, String message) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception ignored) {
            return "{\"error\":\"Không thể tìm kiếm kho lúc này.\"}";
        }
    }
}
