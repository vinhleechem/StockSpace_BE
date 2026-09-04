package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.WarehouseStockOverviewResponse;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyStockTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final StockBatchService stockBatchService;
    private final TenantWarehouseAccessService accessService;

    @Override
    public String getName() { return "getMyStock"; }

    @Override
    public String getDescription() {
        return "Xem tồn kho của người thuê đang đăng nhập. Mặc định dùng kho đang mở trên giao diện; "
                + "truyền warehouseId để xem kho khác mà người thuê đang có hợp đồng hiệu lực.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "warehouseId", Map.of("type", "string",
                        "description", "UUID kho cần xem. Bỏ trống để dùng kho đang mở trên giao diện."),
                "page", Map.of("type", "integer", "minimum", 0),
                "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return readStock(params, userId, warehouseIdFromParams(params));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        UUID userId = context == null ? null : context.userId();
        // AI-supplied warehouseId takes priority over the page context warehouse
        UUID explicitId = warehouseIdFromParams(params);
        UUID warehouseId = explicitId != null ? explicitId
                : (context == null ? null : context.activeWarehouseId());
        return readStock(params, userId, warehouseId);
    }

    private String readStock(Map<String, Object> params, UUID userId, UUID warehouseId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem tồn kho.\"}";
        }
        if (warehouseId == null) {
            List<Warehouse> active = accessService.findActiveContractWarehouses(userId);
            if (active != null && active.size() == 1) {
                warehouseId = active.get(0).getId();
            } else if (active != null && active.size() > 1) {
                List<String> names = active.stream().map(Warehouse::getName).toList();
                return "{\"error\":\"Người dùng đang thuê nhiều kho: " + String.join(", ", names)
                        + ". Hãy hỏi người dùng muốn kiểm tra tồn kho tại kho nào theo TÊN KHO, tuyệt đối không dùng mã ID.\"}";
            } else {
                return "{\"error\":\"Bạn chưa có hợp đồng thuê kho nào đang hiệu lực để xem tồn kho.\"}";
            }
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
