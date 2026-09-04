package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.wms.putaway.PutawayInputItem;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionItem;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionResult;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestedAllocation;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionService;
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
public class SuggestPutawayTool implements ChatTool {

    private static final int MAX_ITEMS = 10;

    private final ObjectMapper objectMapper;
    private final PutawaySuggestionService suggestionService;

    @Override
    public String getName() {
        return "suggestPutaway";
    }

    @Override
    public String getDescription() {
        return "Tính thử vị trí kệ/ô chứa phù hợp cho hàng sắp nhập tại kho đang chọn dựa trên sức chứa vật lý hiện tại. "
                + "Kết quả chỉ là gợi ý tại thời điểm đọc, không giữ chỗ và không tạo phiếu nhập.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return itemListSchema("Danh sách SKU và số lượng cần xếp");
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
        return read(params, context);
    }

    private String read(Map<String, Object> params, ChatRequestContext context) {
        if (context == null || context.userId() == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem gợi ý xếp hàng.\"}";
        }
        if (context.activeWarehouseId() == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }
        try {
            PutawaySuggestionResult result = suggestionService.suggest(
                    context.userId(), null, context.activeWarehouseId(), parseItems(params));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("warehouseName", context.activeWarehouseName());
            response.put("notice", "Gợi ý chỉ là bản xem trước, không giữ chỗ.");
            response.put("items", result.items().stream().map(this::item).toList());
            return objectMapper.writeValueAsString(response);
        } catch (IllegalArgumentException exception) {
            return "{\"error\":\"Danh sách SKU hoặc số lượng cần xếp không hợp lệ.\"}";
        } catch (Exception exception) {
            log.warn("[SuggestPutawayTool] Preview failed (cause={})",
                    exception.getClass().getSimpleName());
            return "{\"error\":\"Không thể tính gợi ý xếp hàng lúc này. Hãy kiểm tra gói dịch vụ, sơ đồ kho và thông số vật lý của SKU.\"}";
        }
    }

    private List<PutawayInputItem> parseItems(Map<String, Object> params) {
        Object rawItems = params == null ? null : params.get("items");
        if (!(rawItems instanceof List<?> values) || values.isEmpty() || values.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("items must contain 1-20 entries");
        }
        List<PutawayInputItem> items = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException("item must be an object");
            }
            UUID skuId = UUID.fromString(String.valueOf(row.get("skuId")).trim());
            int quantity = Integer.parseInt(String.valueOf(row.get("quantity")));
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            items.add(new PutawayInputItem(skuId, quantity));
        }
        return items;
    }

    private Map<String, Object> item(PutawaySuggestionItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", item.skuCode());
        result.put("skuName", item.skuName());
        result.put("requestedQuantity", item.requestedQuantity());
        result.put("unallocatedQuantity", item.unallocatedQuantity());
        result.put("warning", item.warning());
        result.put("allocations", item.allocations().stream().limit(20).map(this::allocation).toList());
        result.put("allocationsTruncated", item.allocations().size() > 20);
        return result;
    }

    private Map<String, Object> allocation(PutawaySuggestedAllocation allocation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rack", allocation.capacity() == null ? null : allocation.capacity().rack().name());
        result.put("bin", allocation.capacity() == null ? null : allocation.capacity().bin().name());
        result.put("quantity", allocation.quantity());
        result.put("reasons", allocation.reasons());
        return result;
    }

    private Map<String, Object> itemListSchema(String description) {
        Map<String, Object> itemProperties = new LinkedHashMap<>();
        itemProperties.put("skuId", Map.of("type", "string"));
        itemProperties.put("quantity", Map.of("type", "integer", "minimum", 1));
        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "object");
        itemSchema.put("properties", itemProperties);
        itemSchema.put("required", List.of("skuId", "quantity"));
        return Map.of(
                "type", "object",
                "properties", Map.of("items", Map.of(
                        "type", "array", "description", description,
                        "minItems", 1, "maxItems", MAX_ITEMS, "items", itemSchema)),
                "required", List.of("items"));
    }
}
