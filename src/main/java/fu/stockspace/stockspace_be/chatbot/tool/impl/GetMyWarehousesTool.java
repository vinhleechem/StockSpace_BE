package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyWarehousesTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseRepository warehouseRepository;

    @Override
    public String getName() {
        return "getMyWarehouses";
    }

    @Override
    public String getDescription() {
        return "Xem danh sách các kho bãi do chủ kho (Owner) đang đăng nhập sở hữu và quản lý.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Owner để xem danh sách kho.\"}";
        }

        try {
            List<Warehouse> warehouses = warehouseRepository.findByOwnerId(userId, PageRequest.of(0, 50)).getContent();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Warehouse w : warehouses) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("warehouseId", w.getId());
                map.put("name", w.getName());
                map.put("address", w.getAddress());
                map.put("status", w.getStatus() != null ? w.getStatus().name() : "UNKNOWN");
                map.put("capacity", w.getCapacity());
                map.put("rentalPrice", w.getRentalPrice() != null ? w.getRentalPrice() : w.getPricePerMonth());
                map.put("rentalPricingType", w.getRentalPricingType() != null
                        ? w.getRentalPricingType().name() : "FIXED_MONTHLY");
                map.put("pricePerMonth", w.getPricePerMonth());
                result.add(map);
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetMyWarehousesTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách kho lúc này.\"}";
        }
    }
}
