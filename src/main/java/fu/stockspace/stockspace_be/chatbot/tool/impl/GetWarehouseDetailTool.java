package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        return "Lấy thông tin công khai của một kho bãi đang sẵn sàng cho thuê theo mã kho: " +
               "địa chỉ, diện tích, giá thuê, loại kho, mô tả và trạng thái xác minh.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "string", "description", "Mã kho bãi đang sẵn sàng cho thuê cần xem")
                ),
                "required", List.of("warehouseId")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            Object rawWarehouseId = params == null ? null : params.get("warehouseId");
            String idStr = rawWarehouseId instanceof String value ? value.trim() : null;
            if (idStr == null || idStr.isBlank()) {
                return "{\"error\":\"Thiếu mã kho bãi\"}";
            }

            UUID warehouseId = UUID.fromString(idStr);
            Optional<Warehouse> opt = warehouseRepository.findPublicAvailableById(warehouseId);
            if (opt.isEmpty()) {
                return "{\"error\":\"Kho bãi không tồn tại hoặc không còn khả dụng\"}";
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
            detail.put("status", ChatToolLocalization.warehouseStatus(w.getStatus()));
            detail.put("isVerified", w.isVerified());

            return objectMapper.writeValueAsString(detail);

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã kho bãi không hợp lệ\"}";
        } catch (Exception e) {
            log.warn("[GetWarehouseDetailTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin kho\"}";
        }
    }
}
