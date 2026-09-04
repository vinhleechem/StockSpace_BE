package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.WarehouseStockOverviewResponse;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

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
        return "Xem tồn kho của người thuê đang đăng nhập tại kho đang được chọn: tổng số SKU, số lô, số lượng "
                + "và tối đa 20 SKU kèm đơn vị tính, khối lượng, thể tích. Chỉ đọc kho có hợp đồng hiệu lực.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "page", Map.of("type", "integer", "minimum", 0),
                "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return readStock(params, userId, warehouseIdFromParams(params));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return readStock(params,
                context == null ? null : context.userId(),
                context == null ? null : context.activeWarehouseId());
    }

    private String readStock(Map<String, Object> params, UUID userId, UUID warehouseId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem tồn kho.\"}";
        }
        if (warehouseId == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }

        try {
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 15, 30);
            StockBatchService.WarehouseStockSummary summary =
                    stockBatchService.getStockSummaryByWarehouse(userId, warehouseId);
            PagedResponse<WarehouseStockOverviewResponse> overview =
                    stockBatchService.getStockOverviewByWarehouse(
                            userId, warehouseId, PageRequest.of(pageNumber, pageSize));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", summary.warehouseName());
            result.put("productCount", summary.productCount());
            result.put("batchCount", summary.batchCount());
            result.put("totalQuantity", summary.totalQuantity());
            result.put("products", overview.getContent().stream().map(this::toProductSummary).toList());
            result.put("productsReturned", overview.getContent().size());
            result.put("productsTotal", overview.getTotalElements());
            result.put("page", overview.getPage());
            result.put("totalPages", overview.getTotalPages());
            result.put("hasMore", !overview.isLast());
            return objectMapper.writeValueAsString(result);

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã kho bãi không hợp lệ\"}";
        } catch (Exception e) {
            log.warn("[GetMyStockTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin tồn kho lúc này.\"}";
        }
    }

    private Map<String, Object> toProductSummary(WarehouseStockOverviewResponse stock) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", stock.getSkuCode());
        result.put("skuName", stock.getSkuName());
        result.put("category", stock.getCategoryName());
        result.put("unit", stock.getUomSymbol());
        result.put("quantity", stock.getTotalQuantity());
        result.put("weightKg", stock.getTotalWeightKg());
        result.put("volumeM3", stock.getTotalVolumeM3());
        return result;
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
