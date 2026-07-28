package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool: getWarehouseDetail
 * Lấy chi tiết một kho bãi theo ID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetWarehouseDetailTool implements ChatTool {

    private final WarehouseRepository warehouseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() { return "getWarehouseDetail"; }

    @Override
    public String getDescription() {
        return "Lấy thông tin chi tiết của một kho bãi cụ thể theo ID: " +
               "địa chỉ đầy đủ, diện tích, giá thuê, mô tả, trạng thái xác minh.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "STRING", "description", "ID của kho bãi cần xem chi tiết")
                ),
                "required", List.of("warehouseId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            String idStr = (String) params.get("warehouseId");
            if (idStr == null || idStr.isBlank()) {
                return "{\"error\": \"Thiếu warehouseId\"}";
            }

            UUID warehouseId = UUID.fromString(idStr);
            Optional<Warehouse> opt = warehouseRepository.findById(warehouseId);
            if (opt.isEmpty()) {
                return "{\"error\": \"Kho bãi không tồn tại\"}";
            }

            Warehouse w = opt.get();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", w.getId().toString());
            detail.put("name", w.getName());
            detail.put("address", w.getAddress());
            detail.put("description", w.getDescription());
            detail.put("pricePerMonth", w.getPricePerMonth());
            detail.put("capacity", w.getCapacity());
            detail.put("type", w.getType() != null ? w.getType().getName() : null);
            detail.put("status", w.getStatus().name());
            detail.put("isVerified", w.isVerified());

            return objectMapper.writeValueAsString(detail);

        } catch (IllegalArgumentException e) {
            return "{\"error\": \"warehouseId không hợp lệ\"}";
        } catch (Exception e) {
            log.error("[GetWarehouseDetailTool] Error: {}", e.getMessage(), e);
            return "{\"error\": \"Không thể lấy thông tin kho\"}";
        }
    }
}
