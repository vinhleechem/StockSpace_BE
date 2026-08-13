package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;

import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool: getPendingOutboundOrders
 * Xem danh sách các phiếu xuất kho (OUTBOUND) đang ở trạng thái PENDING chờ xử lý.
 */
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
        return "Xem danh sách các phiếu xuất kho (OUTBOUND) đang chờ duyệt / dỡ hàng (PENDING).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "warehouseId", Map.of(
                                "type", "string",
                                "description", "Mã UUID của kho bãi (tùy chọn)"
                        )
                )
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem danh sách phiếu xuất kho.\"}";
        }

        try {
            UUID warehouseId = null;
            if (params != null && params.containsKey("warehouseId") && params.get("warehouseId") != null) {
                try {
                    warehouseId = UUID.fromString(params.get("warehouseId").toString());
                } catch (IllegalArgumentException ignored) {}
            }

            PagedResponse<InventoryReceiptResponse> paged = receiptService.getReceiptsByWarehouse(
                    userId, warehouseId, DocumentType.OUTBOUND, PageRequest.of(0, 50));
            List<Map<String, Object>> pendingItems = new ArrayList<>();
            for (InventoryReceiptResponse item : paged.getContent()) {

                if (item.getStatus() == ApprovalStatus.PENDING) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("receiptId", item.getId());
                    m.put("warehouseId", item.getWarehouseId());
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
}
