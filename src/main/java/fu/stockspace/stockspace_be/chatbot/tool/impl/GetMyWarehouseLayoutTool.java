package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyWarehouseLayoutTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final WarehouseLayoutService layoutService;

    @Override
    public String getName() {
        return "getMyWarehouseLayout";
    }

    @Override
    public String getDescription() {
        return "Xem sơ đồ vận hành của người thuê tại kho đang được chọn, gồm kích thước, kệ, ô chứa và trạng thái sử dụng ô.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            return read(new ChatRequestContext(
                    userId, ChatToolParameters.optionalUuid(params, "warehouseId")));
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Mã kho không hợp lệ.\"}";
        }
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return read(context);
    }

    private String read(ChatRequestContext context) {
        if (context == null || context.userId() == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem sơ đồ kho đang thuê.\"}";
        }
        if (context.activeWarehouseId() == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }
        try {
            WarehouseLayoutResponse layout = layoutService.getLayoutTree(
                    context.activeWarehouseId(), context.userId(), "TENANT");
            Map<String, Object> result = new LinkedHashMap<>(WarehouseLayoutToolMapper.toSafeMap(layout));
            result.put("warehouseName", context.activeWarehouseName());
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            log.warn("[GetMyWarehouseLayoutTool] Read failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy sơ đồ vận hành của kho này.\"}";
        }
    }
}
