package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.*;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetOccupancyTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseRepository warehouseRepository;

    @Override
    public String getName() {
        return "getOccupancyRate";
    }

    @Override
    public String getDescription() {
        return "Xem tỷ lệ lấp đầy kho (tỷ lệ kho đã được thuê RENTED) của Chủ kho (Owner).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Owner để xem tỷ lệ lấp đầy kho.\"}";
        }

        try {
            List<Warehouse> warehouses = warehouseRepository.findByOwnerId(userId, Pageable.unpaged()).getContent();
            int total = warehouses.size();
            int rented = 0;
            List<String> rentedWarehouses = new ArrayList<>();
            List<String> availableWarehouses = new ArrayList<>();

            for (Warehouse w : warehouses) {
                if (w.getStatus() == WarehouseStatus.RENTED) {
                    rented++;
                    rentedWarehouses.add(w.getName());
                } else if (w.getStatus() == WarehouseStatus.AVAILABLE) {
                    availableWarehouses.add(w.getName());
                }
            }

            double rate = total > 0 ? ((double) rented / total) * 100.0 : 0.0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("totalWarehouses", total);
            result.put("rentedWarehousesCount", rented);
            result.put("availableWarehousesCount", availableWarehouses.size());
            result.put("occupancyRatePercentage", Math.round(rate * 100.0) / 100.0);
            result.put("rentedWarehouseNames", rentedWarehouses);
            result.put("availableWarehouseNames", availableWarehouses);

            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[GetOccupancyTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy tỷ lệ lấp đầy kho lúc này.\"}";
        }
    }
}
