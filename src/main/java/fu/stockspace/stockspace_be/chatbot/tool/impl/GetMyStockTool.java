package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyStockTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final StockBatchService stockBatchService;

    @Override
    public String getName() { return "getMyStock"; }

    @Override
    public String getDescription() {
        return "Xem tóm tắt tồn kho của người thuê đang đăng nhập tại một kho có hợp đồng đang hiệu lực: " +
               "số SKU, số lô và tổng số lượng. Không trả tồn kho của người thuê khác.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "string", "description", "Mã kho bãi cần xem tồn kho")
                ),
                "required", List.of("warehouseId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem tồn kho.\"}";
        }

        try {
            Object rawWarehouseId = params == null ? null : params.get("warehouseId");
            String warehouseIdStr = rawWarehouseId instanceof String value ? value.trim() : null;
            if (warehouseIdStr == null || warehouseIdStr.isBlank()) {
                return "{\"error\":\"Thiếu mã kho bãi\"}";
            }
            UUID warehouseId = UUID.fromString(warehouseIdStr);

            StockBatchService.WarehouseStockSummary summary =
                    stockBatchService.getStockSummaryByWarehouse(userId, warehouseId);
            return objectMapper.writeValueAsString(summary);

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã kho bãi không hợp lệ\"}";
        } catch (Exception e) {
            log.warn("[GetMyStockTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin tồn kho lúc này.\"}";
        }
    }
}
