package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.LinkedHashMap;
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
        return "Xem tóm tắt tồn kho của người thuê đang đăng nhập tại kho đang được chọn có hợp đồng đang hiệu lực: " +
               "số SKU, số lô và tổng số lượng. Không trả tồn kho của người thuê khác.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return readStock(userId, warehouseIdFromParams(params));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return readStock(
                context == null ? null : context.userId(),
                context == null ? null : context.activeWarehouseId());
    }

    private String readStock(UUID userId, UUID warehouseId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem tồn kho.\"}";
        }
        if (warehouseId == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }

        try {
            StockBatchService.WarehouseStockSummary summary =
                    stockBatchService.getStockSummaryByWarehouse(userId, warehouseId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", summary.warehouseName());
            result.put("productCount", summary.productCount());
            result.put("batchCount", summary.batchCount());
            result.put("totalQuantity", summary.totalQuantity());
            return objectMapper.writeValueAsString(result);

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã kho bãi không hợp lệ\"}";
        } catch (Exception e) {
            log.warn("[GetMyStockTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin tồn kho lúc này.\"}";
        }
    }

    private UUID warehouseIdFromParams(Map<String, Object> params) {
        Object rawWarehouseId = params == null ? null : params.get("warehouseId");
        if (rawWarehouseId == null) {
            return null;
        }
        try {
            return UUID.fromString(rawWarehouseId.toString().trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
