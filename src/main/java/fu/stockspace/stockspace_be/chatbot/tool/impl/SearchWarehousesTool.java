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
        return "Tìm các kho đang sẵn sàng cho thuê theo từ khóa (tên, địa chỉ, loại kho, hoặc công năng/loại hàng hóa lưu trữ trong mô tả kho), khoảng giá và diện tích. " +
               "Khi người dùng tìm kiếm kho, hỏi gợi ý kho, hoặc hỏi về kho phù hợp với loại hàng hóa/vật liệu/công năng cụ thể, hãy trích xuất từ khóa và gọi tool này. " +
               "Nếu người dùng chưa nêu tiêu chí, gọi tool với tham số rỗng để lấy danh sách kho sẵn có.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of("type", "string", "description", "Từ khóa tìm kiếm (tên kho, địa chỉ, loại kho hoặc công năng/loại hàng hóa lưu trữ như vật liệu xây dựng, nông sản, kho mát, kho lạnh, linh kiện...)"),
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

            BigDecimal minPrice = getNonNegativeDecimalParam(safeParams, "minPrice");
            BigDecimal maxPrice = getNonNegativeDecimalParam(safeParams, "maxPrice");
            BigDecimal minArea = getNonNegativeDecimalParam(safeParams, "minArea");
            if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
                return "{\"error\":\"Giá thuê tối thiểu không được lớn hơn giá thuê tối đa\"}";
            }

            String keywordLike = keyword != null ? "%" + keyword.toLowerCase(Locale.ROOT) + "%" : null;

            // Tầng 1: Tìm kiếm theo từ khóa chính xác trên tên, địa chỉ, mô tả, loại kho
            var results = warehouseRepository.searchPublic(
                    keywordLike,
                    WarehouseStatus.AVAILABLE,
                    minPrice,
                    maxPrice,
                    minArea,
                    PageRequest.of(0, 5)
            );

            // Thử bỏ tiền tố "kho " nếu chưa ra kết quả (ví dụ "kho vật liệu" -> "vật liệu")
            if (results.isEmpty() && keyword != null && keyword.toLowerCase(Locale.ROOT).startsWith("kho ")) {
                String strippedKeyword = keyword.substring(4).trim();
                if (!strippedKeyword.isBlank()) {
                    results = warehouseRepository.searchPublic(
                            "%" + strippedKeyword.toLowerCase(Locale.ROOT) + "%",
                            WarehouseStatus.AVAILABLE,
                            minPrice,
                            maxPrice,
                            minArea,
                            PageRequest.of(0, 5)
                    );
                }
            }

            // Tầng 2: LLM Semantic Reasoning Fallback
            // Nếu từ khóa cụ thể không khớp chính xác trong DB (ví dụ khách dùng từ đồng nghĩa, từ địa phương, hoặc câu hỏi gián tiếp),
            // lấy danh sách toàn bộ các kho đang mở (AVAILABLE) để AI tự đọc hiểu mô tả và suy luận kho phù hợp nhất.
            if (results.isEmpty() && keyword != null && !keyword.isBlank()) {
                var fallbackResults = warehouseRepository.searchPublic(
                        null,
                        WarehouseStatus.AVAILABLE,
                        minPrice,
                        maxPrice,
                        minArea,
                        PageRequest.of(0, 8)
                );

                if (!fallbackResults.isEmpty()) {
                    List<Map<String, Object>> fallbackWarehouses = fallbackResults.getContent().stream()
                            .map(this::toMap)
                            .collect(Collectors.toList());

                    Map<String, Object> fallbackResult = new HashMap<>();
                    fallbackResult.put("total", fallbackResults.getTotalElements());
                    fallbackResult.put("warehouses", fallbackWarehouses);
                    fallbackResult.put("matchedByExactKeyword", false);
                    fallbackResult.put("instructionForAI",
                            "Không tìm thấy kho khớp chính xác cụm từ '" + keyword + "'. " +
                            "Dưới đây là danh sách các kho đang mở cho thuê kèm mô tả chi tiết, đặc điểm và loại kho. " +
                            "Hãy đọc kỹ mô tả (description), loại kho (type) và thông số từng kho để phân tích xem kho nào thích hợp nhất với nhu cầu của người dùng và tư vấn, giải thích lý do cụ thể."
                    );
                    return objectMapper.writeValueAsString(fallbackResult);
                }
            }

            List<Map<String, Object>> warehouses = results.getContent().stream()
                    .map(this::toMap)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("total", results.getTotalElements());
            result.put("warehouses", warehouses);
            if (warehouses.isEmpty()) {
                result.put("message", "Hiện tại hệ thống không tìm thấy kho nào sẵn sàng cho thuê phù hợp với bộ lọc.");
            }
            return objectMapper.writeValueAsString(result);

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            log.warn("[SearchWarehousesTool] Search failed", e);
            return "{\"error\":\"Không thể tìm kiếm kho lúc này.\"}";
        }
    }

    private Map<String, Object> toMap(Warehouse w) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", w.getId().toString());
        map.put("name", w.getName());
        map.put("address", w.getAddress());
        map.put("description", w.getDescription());
        map.put("rentalPrice", w.getRentalPrice());
        map.put("rentalPricingType", w.getRentalPricingType() != null
                ? w.getRentalPricingType().name() : "FIXED_MONTHLY");
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
