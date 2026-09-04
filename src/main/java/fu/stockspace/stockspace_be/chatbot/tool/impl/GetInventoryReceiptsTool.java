package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.ReceiptItemResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetInventoryReceiptsTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final InventoryReceiptService receiptService;

    @Override
    public String getName() {
        return "getInventoryReceipts";
    }

    @Override
    public String getDescription() {
        return "Xem phiếu nhập hoặc xuất tại kho đang được chọn của người thuê. Có thể xem danh sách mới nhất "
                + "hoặc chi tiết một phiếu; đây là dữ liệu đọc, không duyệt hay thay đổi phiếu.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "receiptId", Map.of("type", "string", "description", "Mã phiếu nếu cần xem chi tiết"),
                        "type", Map.of("type", "string", "enum", List.of("INBOUND", "OUTBOUND"),
                                "description", "Loại phiếu: nhập kho hoặc xuất kho"),
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
            return "{\"error\":\"Bạn cần đăng nhập để xem phiếu nhập xuất.\"}";
        }
        try {
            UUID receiptId = optionalUuid(params, "receiptId");
            if (receiptId != null) {
                return objectMapper.writeValueAsString(toDetail(receiptService.getReceiptDetail(userId, receiptId)));
            }

            UUID warehouseId = context.activeWarehouseId();
            if (warehouseId == null) {
                return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
            }
            DocumentType type = optionalType(params);
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 10, 30);
            PagedResponse<InventoryReceiptResponse> page = receiptService.getReceiptsByWarehouse(
                    userId, warehouseId, type,
                    PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", page.getContent().isEmpty() ? context.activeWarehouseName()
                    : page.getContent().get(0).getWarehouseName());
            result.put("receipts", page.getContent().stream().map(this::toSummary).toList());
            result.put("total", page.getTotalElements());
            result.put("page", page.getPage());
            result.put("totalPages", page.getTotalPages());
            result.put("hasMore", !page.isLast());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã phiếu hoặc loại phiếu không hợp lệ.\"}";
        } catch (Exception e) {
            log.warn("[GetInventoryReceiptsTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy phiếu nhập xuất lúc này.\"}";
        }
    }

    private Map<String, Object> toSummary(InventoryReceiptResponse receipt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", receipt.getId());
        result.put("type", receipt.getType() == DocumentType.INBOUND ? "Phiếu nhập" : "Phiếu xuất");
        result.put("status", ChatToolLocalization.approvalStatus(receipt.getStatus()));
        result.put("itemCount", receipt.getItems() == null ? 0 : receipt.getItems().size());
        result.put("totalQuantity", receipt.getItems() == null ? 0
                : receipt.getItems().stream().mapToInt(ReceiptItemResponse::getQuantity).sum());
        result.put("rejectReason", receipt.getRejectReason());
        result.put("createdAt", receipt.getCreatedAt());
        result.put("updatedAt", receipt.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toDetail(InventoryReceiptResponse receipt) {
        Map<String, Object> result = new LinkedHashMap<>(toSummary(receipt));
        result.put("warehouseName", receipt.getWarehouseName());
        result.put("senderName", receipt.getSenderName());
        result.put("receiverName", receipt.getReceiverName());
        result.put("items", receipt.getItems() == null ? List.of()
                : receipt.getItems().stream().map(this::toItem).toList());
        return result;
    }

    private Map<String, Object> toItem(ReceiptItemResponse item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", item.getSkuCode());
        result.put("skuName", item.getSkuName());
        result.put("quantity", item.getQuantity());
        result.put("rack", item.getRackName());
        result.put("bin", item.getBinName());
        result.put("pickSequence", item.getPickSequence());
        result.put("note", item.getNote());
        return result;
    }

    private DocumentType optionalType(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("type");
        return raw == null ? null : DocumentType.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
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
