package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool: searchWarehouses
 * Tìm kiếm kho công khai theo bộ lọc (city/keyword, loại, giá, diện tích).
 * Trả về tối đa 5 kho phù hợp nhất.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchWarehousesTool implements ChatTool {

    private final WarehouseRepository warehouseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() { return "searchWarehouses"; }

    @Override
    public String getDescription() {
        return "Tìm kiếm kho bãi đang cho thuê theo thành phố/địa điểm, loại kho, khoảng giá hoặc diện tích tối thiểu. " +
               "Trả về danh sách tối đa 5 kho phù hợp.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "keyword", Map.of("type", "STRING", "description", "Từ khóa tìm kiếm: tên kho hoặc địa chỉ/thành phố"),
                        "minPrice", Map.of("type", "NUMBER", "description", "Giá thuê tối thiểu (VNĐ/tháng)"),
                        "maxPrice", Map.of("type", "NUMBER", "description", "Giá thuê tối đa (VNĐ/tháng)"),
                        "minArea",  Map.of("type", "NUMBER", "description", "Diện tích tối thiểu (m²)")
                )
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            String keyword = getStringParam(params, "keyword");
            String keywordLike = (keyword != null) ? "%" + keyword.toLowerCase() + "%" : null;

            BigDecimal minPrice = getDecimalParam(params, "minPrice");
            BigDecimal maxPrice = getDecimalParam(params, "maxPrice");
            BigDecimal minArea  = getDecimalParam(params, "minArea");

            var results = warehouseRepository.searchPublic(
                    keywordLike,
                    null,  // status = null → default AVAILABLE
                    minPrice,
                    maxPrice,
                    minArea,
                    PageRequest.of(0, 5)
            );

            List<Map<String, Object>> warehouses = results.getContent().stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("total", results.getTotalElements());
            result.put("warehouses", warehouses);
            if (warehouses.isEmpty()) {
                result.put("message", "Không tìm thấy kho phù hợp với bộ lọc này.");
            }
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            log.error("[SearchWarehousesTool] Error: {}", e.getMessage(), e);
            return "{\"error\": \"Không thể tìm kiếm kho lúc này.\"}";
        }
    }

    private Map<String, Object> toMap(Warehouse w) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", w.getId().toString());
        map.put("name", w.getName());
        map.put("address", w.getAddress());
        map.put("pricePerMonth", w.getPricePerMonth());
        map.put("capacity", w.getCapacity());
        map.put("type", w.getType() != null ? w.getType().getName() : null);
        map.put("isVerified", w.isVerified());
        map.put("status", w.getStatus().name());
        return map;
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return (val instanceof String s && !s.isBlank()) ? s : null;
    }

    private BigDecimal getDecimalParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) return null;
        try { return new BigDecimal(val.toString()); }
        catch (Exception e) { return null; }
    }
}
