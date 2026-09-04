package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseTypeResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetWarehouseTypesTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseTypeService warehouseTypeService;

    @Override
    public String getName() {
        return "getWarehouseTypes";
    }

    @Override
    public String getDescription() {
        return "Liệt kê các loại kho hiện có trên StockSpace và mô tả của từng loại để tư vấn hoặc lọc kho phù hợp.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "warehouseTypes", warehouseTypeService.getAllTypes().stream()
                            .map(this::toMap)
                            .toList()));
        } catch (Exception exception) {
            log.warn("[GetWarehouseTypesTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách loại kho lúc này.\"}";
        }
    }

    private Map<String, Object> toMap(WarehouseTypeResponse type) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", type.getName());
        result.put("description", type.getDescription());
        return result;
    }
}
