package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tool: getMyStock
 * Xem tóm tắt tồn kho của Tenant trong một kho cụ thể.
 *
 * ⚠️ PENDING: Chờ Dev B expose StockBatchService.getSummaryByWarehouse(UUID warehouseId)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyStockTool implements ChatTool {

    private final ObjectMapper objectMapper;

    // TODO: Inject StockBatchService khi Dev B expose getSummaryByWarehouse()
    // private final StockBatchService stockBatchService;

    @Override
    public String getName() { return "getMyStock"; }

    @Override
    public String getDescription() {
        return "Xem tóm tắt tồn kho của Tenant trong một kho bãi: tổng số sản phẩm, " +
               "số lô hàng, khối lượng tồn kho hiện tại.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "STRING", "description", "ID của kho bãi cần xem tồn kho")
                ),
                "required", List.of("warehouseId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            String warehouseIdStr = (String) params.get("warehouseId");
            if (warehouseIdStr == null || warehouseIdStr.isBlank()) {
                return "{\"error\": \"Thiếu warehouseId\"}";
            }
            UUID warehouseId = UUID.fromString(warehouseIdStr);

            // TODO: Uncomment khi Dev B expose method:
            // Object summary = stockBatchService.getSummaryByWarehouse(warehouseId);
            // return objectMapper.writeValueAsString(summary);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "pending_integration",
                    "warehouseId", warehouseIdStr,
                    "message", "Chức năng đang được phát triển, vui lòng thử lại sau."
            ));

        } catch (IllegalArgumentException e) {
            return "{\"error\": \"warehouseId không hợp lệ\"}";
        } catch (Exception e) {
            log.error("[GetMyStockTool] Error: {}", e.getMessage(), e);
            return "{\"error\": \"Không thể lấy thông tin tồn kho lúc này.\"}";
        }
    }
}
