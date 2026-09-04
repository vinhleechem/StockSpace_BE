package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditItemResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditResponse;
import fu.stockspace.stockspace_be.wms.stock.service.InventoryAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetInventoryAuditsTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final InventoryAuditService auditService;

    @Override
    public String getName() {
        return "getInventoryAudits";
    }

    @Override
    public String getDescription() {
        return "Xem các đợt kiểm kê của người thuê tại kho đang được chọn hoặc chi tiết một đợt kiểm kê, "
                + "bao gồm chênh lệch giữa số lượng hệ thống và số lượng thực tế.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "auditId", Map.of("type", "string", "description", "Mã đợt kiểm kê nếu cần xem chi tiết"),
                        "page", Map.of("type", "integer", "minimum", 0),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)
                )
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return read(params, new ChatRequestContext(userId, warehouseIdFromParams(params)));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return read(params, context);
    }

    private String read(Map<String, Object> params, ChatRequestContext context) {
        UUID userId = context == null ? null : context.userId();
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem kiểm kê.\"}";
        }
        try {
            UUID auditId = optionalUuid(params, "auditId");
            if (auditId != null) {
                return objectMapper.writeValueAsString(toDetail(auditService.getAuditDetail(userId, auditId)));
            }
            UUID warehouseId = context.activeWarehouseId();
            if (warehouseId == null) {
                return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
            }
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 10, 30);
            PagedResponse<InventoryAuditResponse> page = auditService.getMyAudits(
                    userId, warehouseId,
                    PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", page.getContent().isEmpty() ? context.activeWarehouseName()
                    : page.getContent().get(0).getWarehouseName());
            result.put("audits", page.getContent().stream().map(this::toSummary).toList());
            result.put("total", page.getTotalElements());
            result.put("page", page.getPage());
            result.put("totalPages", page.getTotalPages());
            result.put("hasMore", !page.isLast());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã kiểm kê không hợp lệ.\"}";
        } catch (Exception e) {
            log.warn("[GetInventoryAuditsTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy dữ liệu kiểm kê lúc này.\"}";
        }
    }

    private Map<String, Object> toSummary(InventoryAuditResponse audit) {
        List<InventoryAuditItemResponse> items = audit.getItems() == null ? List.of() : audit.getItems();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", audit.getId());
        result.put("status", ChatToolLocalization.auditStatus(audit.getStatus()));
        result.put("note", audit.getNote());
        result.put("itemCount", items.size());
        result.put("itemsWithDiscrepancy", items.stream()
                .filter(item -> item.getDiscrepancy() != null && item.getDiscrepancy() != 0).count());
        result.put("netDiscrepancy", items.stream().filter(item -> item.getDiscrepancy() != null)
                .mapToInt(InventoryAuditItemResponse::getDiscrepancy).sum());
        result.put("createdAt", audit.getCreatedAt());
        result.put("updatedAt", audit.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toDetail(InventoryAuditResponse audit) {
        Map<String, Object> result = new LinkedHashMap<>(toSummary(audit));
        result.put("warehouseName", audit.getWarehouseName());
        result.put("items", audit.getItems() == null ? List.of()
                : audit.getItems().stream().map(this::toItem).toList());
        return result;
    }

    private Map<String, Object> toItem(InventoryAuditItemResponse item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", item.getSkuCode());
        result.put("skuName", item.getSkuName());
        result.put("unit", item.getUomSymbol());
        result.put("rack", item.getRackName());
        result.put("bin", item.getBinName());
        result.put("expectedQuantity", item.getExpectedQuantity());
        result.put("actualQuantity", item.getActualQuantity());
        result.put("discrepancy", item.getDiscrepancy());
        result.put("note", item.getNote());
        return result;
    }

    private UUID optionalUuid(Map<String, Object> params, String key) {
        Object raw = params == null ? null : params.get(key);
        return raw == null || raw.toString().isBlank() ? null : UUID.fromString(raw.toString().trim());
    }

    private UUID warehouseIdFromParams(Map<String, Object> params) {
        try {
            return optionalUuid(params, "warehouseId");
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
