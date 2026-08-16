package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;

import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetPendingOutboundTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final InventoryReceiptService receiptService;

    @Override
    public String getName() {
        return "getPendingOutboundOrders";
    }

    @Override
    public String getDescription() {
        return "Xem danh sách các phiếu xuất kho (OUTBOUND) đang chờ duyệt / dỡ hàng (PENDING) của kho đang được chọn.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return readPendingOutbound(userId, warehouseIdFromParams(params));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return readPendingOutbound(
                context == null ? null : context.userId(),
                context == null ? null : context.activeWarehouseId());
    }

    private String readPendingOutbound(UUID userId, UUID warehouseId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem danh sách phiếu xuất kho.\"}";
        }
        if (warehouseId == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }

        try {
            PagedResponse<InventoryReceiptResponse> paged = receiptService.getReceiptsByWarehouse(
                    userId, warehouseId, DocumentType.OUTBOUND, PageRequest.of(0, 50));
            List<Map<String, Object>> pendingItems = new ArrayList<>();
            for (InventoryReceiptResponse item : paged.getContent()) {

                if (item.getStatus() == ApprovalStatus.PENDING) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("warehouseName", item.getWarehouseName());
                    m.put("type", item.getType());
                    m.put("status", item.getStatus());
                    m.put("items", item.getItems());
                    m.put("createdAt", item.getCreatedAt());
                    pendingItems.add(m);
                }
            }

            return objectMapper.writeValueAsString(pendingItems);
        } catch (Exception e) {
            log.warn("[GetPendingOutboundTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách phiếu xuất kho lúc này.\"}";
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
