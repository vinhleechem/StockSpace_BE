package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingInputItem;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingSuggestionService;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickLineResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickStopResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuggestOutboundPickingTool implements ChatTool {

    private static final int MAX_ITEMS = 10;

    private final ObjectMapper objectMapper;
    private final OutboundPickingSuggestionService suggestionService;

    @Override
    public String getName() {
        return "suggestOutboundPicking";
    }

    @Override
    public String getDescription() {
        return "Tính thử danh sách lấy hàng FIFO và thứ tự đi qua kệ/ô chứa cho hàng sắp xuất tại kho đang chọn. "
                + "Kết quả chỉ là bản xem trước, không giữ tồn và không tạo phiếu xuất.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", Map.of(
                "skuId", Map.of("type", "string"),
                "quantity", Map.of("type", "integer", "minimum", 1)));
        itemSchema.put("required", List.of("skuId", "quantity"));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("warehouseId", Map.of("type", "string",
                "description", "UUID kho cần gợi ý lấy hàng. Bỏ trống để dùng kho đang mở trên giao diện."));
        properties.put("items", Map.of(
                "type", "array", "description", "Danh sách SKU và số lượng cần lấy",
                "minItems", 1, "maxItems", MAX_ITEMS, "items", itemSchema));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("items"));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            return read(params, new ChatRequestContext(userId,
                    ChatToolParameters.optionalUuid(params, "warehouseId")));
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Mã kho không hợp lệ.\"}";
        }
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        // AI-supplied warehouseId takes priority over the page-context warehouse
        UUID explicit = ChatToolParameters.optionalUuid(params, "warehouseId");
        if (explicit != null && context != null) {
            return read(params, new ChatRequestContext(context.userId(), explicit, null));
        }
        return read(params, context);
    }

    private String read(Map<String, Object> params, ChatRequestContext context) {
        if (context == null || context.userId() == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem gợi ý lấy hàng.\"}";
        }
        if (context.activeWarehouseId() == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }
        try {
            OutboundPickingSuggestionResponse suggestion = suggestionService.suggest(
                    context.userId(), null, context.activeWarehouseId(), parseItems(params));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", context.activeWarehouseName());
            result.put("complete", suggestion.complete());
            result.put("strategy", "FIFO theo lô nhập trước, tối ưu thứ tự kệ/ô chứa");
            result.put("warnings", suggestion.warnings());
            result.put("items", suggestion.items().stream().map(item -> Map.of(
                    "requestedQuantity", item.requestedQuantity(),
                    "allocatedQuantity", item.allocatedQuantity(),
                    "shortageQuantity", item.shortageQuantity())).toList());
            result.put("stops", suggestion.stops().stream().limit(50).map(this::stop).toList());
            result.put("stopsTruncated", suggestion.stops().size() > 50);
            result.put("notice", "Bản xem trước không giữ tồn và có thể thay đổi khi tồn kho thay đổi.");
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Danh sách SKU hoặc số lượng cần lấy không hợp lệ.\"}";
        } catch (Exception exception) {
            log.warn("[SuggestOutboundPickingTool] Preview failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể tính gợi ý lấy hàng lúc này. Hãy kiểm tra gói dịch vụ, sơ đồ kho và tồn kho hiện tại.\"}";
        }
    }

    private List<OutboundPickingInputItem> parseItems(Map<String, Object> params) {
        Object rawItems = params == null ? null : params.get("items");
        if (!(rawItems instanceof List<?> values) || values.isEmpty() || values.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("items must contain 1-20 entries");
        }
        List<OutboundPickingInputItem> items = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException("item must be an object");
            }
            UUID skuId = UUID.fromString(String.valueOf(row.get("skuId")).trim());
            int quantity = Integer.parseInt(String.valueOf(row.get("quantity")));
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            items.add(new OutboundPickingInputItem(skuId, quantity));
        }
        return items;
    }

    private Map<String, Object> stop(OutboundPickStopResponse stop) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sequence", stop.sequence());
        result.put("rackCode", stop.rackCode());
        result.put("binCode", stop.binCode());
        result.put("shelfLevel", stop.shelfLevel());
        result.put("lines", stop.lines().stream().map(this::line).toList());
        return result;
    }

    private Map<String, Object> line(OutboundPickLineResponse line) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", line.skuCode());
        result.put("skuName", line.skuName());
        result.put("arrivalDate", line.arrivalDate());
        result.put("quantity", line.quantity());
        return result;
    }
}
