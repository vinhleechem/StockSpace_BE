package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseOwnerContactResponse;
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
public class GetWarehouseOwnerContactTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseService warehouseService;

    @Override
    public String getName() {
        return "getWarehouseOwnerContact";
    }

    @Override
    public String getDescription() {
        return "Lấy tên và số điện thoại liên hệ của bên cho thuê một kho đang được công bố. "
                + "Chỉ dùng khi người thuê đã đăng nhập và chủ động muốn liên hệ về kho cụ thể.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "string", "description", "Mã kho từ kết quả tìm kiếm")
                ),
                "required", List.of("warehouseId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem thông tin liên hệ.\"}";
        }
        try {
            Object rawId = params == null ? null : params.get("warehouseId");
            if (!(rawId instanceof String value) || value.isBlank()) {
                return "{\"error\":\"Thiếu mã kho bãi.\"}";
            }
            WarehouseOwnerContactResponse contact = warehouseService.getOwnerContact(UUID.fromString(value.trim()));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contactName", contact.getOwnerName());
            result.put("phone", contact.getPhone());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã kho bãi không hợp lệ.\"}";
        } catch (Exception e) {
            log.warn("[GetWarehouseOwnerContactTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin liên hệ lúc này.\"}";
        }
    }
}
