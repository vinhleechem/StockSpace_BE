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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;






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
        return "Tìm tối đa 5 kho đang sẵn sàng cho thuê theo từ khóa trong tên hoặc địa chỉ, khoảng giá thuê " +
               "và diện tích tối thiểu. Khi ý định là tìm hoặc xem kho nhưng chưa nêu tiêu chí, phải gọi tool " +
               "này với các tham số rỗng để lấy danh sách mặc định. " +
               "Kết quả được lọc theo dữ liệu có cấu trúc, không phải tìm kiếm ngữ nghĩa.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "Từ khóa khớp với tên kho hoặc địa chỉ"),
                        "minPrice", Map.of("type", "number", "description", "Giá thuê tối thiểu (VNĐ/tháng), không âm"),
                        "maxPrice", Map.of("type", "number", "description", "Giá thuê tối đa (VNĐ/tháng), không âm"),
                        "minArea",  Map.of("type", "number", "description", "Diện tích tối thiểu (m²), không âm")
                )
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            Map<String, Object> safeParams = params == null ? Map.of() : params;
            String keyword = getStringParam(safeParams, "keyword");
            String keywordLike = keyword != null ? "%" + keyword.toLowerCase(Locale.ROOT) + "%" : null;

            BigDecimal minPrice = getNonNegativeDecimalParam(safeParams, "minPrice");
            BigDecimal maxPrice = getNonNegativeDecimalParam(safeParams, "maxPrice");
            BigDecimal minArea = getNonNegativeDecimalParam(safeParams, "minArea");
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                return "{\"error\":\"Giá thuê tối thiểu không được lớn hơn giá thuê tối đa\"}";
            }

            var results = warehouseRepository.searchPublic(
                    keywordLike,
                    WarehouseStatus.AVAILABLE,
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

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.warn("[SearchWarehousesTool] Search failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể tìm kiếm kho lúc này.\"}";
        }
    }

    private Map<String, Object> toMap(Warehouse w) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", w.getId().toString());
        map.put("name", w.getName());
        map.put("address", w.getAddress());
        map.put("description", w.getDescription());
        map.put("pricePerMonth", w.getPricePerMonth());
        map.put("capacity", w.getCapacity());
        map.put("type", w.getType() != null ? w.getType().getName() : null);
        map.put("isVerified", w.isVerified());
        map.put("status", ChatToolLocalization.warehouseStatus(w.getStatus()));
        return map;
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val instanceof String s && !s.isBlank() ? s.trim() : null;
    }

    private BigDecimal getNonNegativeDecimalParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null) {
            return null;
        }

        try {
            BigDecimal value = new BigDecimal(val.toString());
            if (value.signum() < 0) {
                throw new IllegalArgumentException(
                        ChatToolLocalization.filterLabel(key) + " không được là số âm"
                );
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    ChatToolLocalization.filterLabel(key) + " phải là một số hợp lệ"
            );
        }
    }
}
