package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetPublicWarehouseLayoutTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseLayoutService layoutService;

    @Override
    public String getName() {
        return "getPublicWarehouseLayout";
    }

    @Override
    public String getDescription() {
        return "Xem sơ đồ công khai của một bài đăng kho còn hiệu lực: kích thước, kệ, ô chứa và sức chứa thiết kế. "
                + "Chỉ gọi sau khi đã có mã kho từ kết quả tìm kiếm hoặc chi tiết kho.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of("warehouseId", Map.of(
                        "type", "string", "description", "Mã kho lấy từ kết quả tool")),
                "required", java.util.List.of("warehouseId"));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            UUID warehouseId = ChatToolParameters.optionalUuid(params, "warehouseId");
            if (warehouseId == null) {
                throw new IllegalArgumentException("warehouseId is required");
            }
            WarehouseLayoutResponse layout = layoutService.getLayoutTree(warehouseId, null, "PUBLIC");
            return objectMapper.writeValueAsString(WarehouseLayoutToolMapper.toSafeMap(layout));
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Mã kho không hợp lệ.\"}";
        } catch (Exception exception) {
            log.warn("[GetPublicWarehouseLayoutTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy sơ đồ công khai của kho này.\"}";
        }
    }
}
