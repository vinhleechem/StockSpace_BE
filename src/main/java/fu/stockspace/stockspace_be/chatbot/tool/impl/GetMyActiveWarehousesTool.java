package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyActiveWarehousesTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseService warehouseService;

    @Override
    public String getName() {
        return "getMyActiveWarehouses";
    }

    @Override
    public String getDescription() {
        return "Liệt kê các kho mà người thuê hiện có hợp đồng hiệu lực để người dùng biết kho nào có thể chọn và xem dữ liệu WMS.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem các kho đang thuê.\"}";
        }
        try {
            List<Map<String, Object>> warehouses = warehouseService.getActiveContractWarehouses(userId)
                    .stream().map(this::toMap).toList();
            return objectMapper.writeValueAsString(Map.of(
                    "warehouses", warehouses,
                    "total", warehouses.size()));
        } catch (Exception exception) {
            log.warn("[GetMyActiveWarehousesTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách kho đang thuê lúc này.\"}";
        }
    }

    private Map<String, Object> toMap(WarehouseResponse warehouse) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", warehouse.getId());
        result.put("name", warehouse.getName());
        result.put("address", warehouse.getAddress());
        result.put("province", warehouse.getProvinceName());
        result.put("district", warehouse.getDistrictName());
        result.put("type", warehouse.getTypeName());
        result.put("capacity", warehouse.getCapacity());
        return result;
    }
}
