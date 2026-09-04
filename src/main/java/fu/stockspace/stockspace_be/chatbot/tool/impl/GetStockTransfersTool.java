package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GetStockTransfersTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final StockTransferService transferService;

    @Override
    public String getName() {
        return "getStockTransfers";
    }

    @Override
    public String getDescription() {
        return "Xem yêu cầu chuyển hàng giữa các kho của người thuê, có thể lọc trạng thái hoặc xem chi tiết. "
                + "Khi đang chọn một kho, kết quả gồm cả chuyến đi và chuyến đến của kho đó.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "string",
                                "description", "UUID kho cần lọc chuyển hàng. Bỏ trống để dùng kho đang mở trên giao diện."),
                        "transferId", Map.of("type", "string", "description", "Mã yêu cầu nếu cần xem chi tiết"),
                        "status", Map.of("type", "string",
                                "enum", List.of("PENDING", "IN_TRANSIT", "COMPLETED", "REJECTED", "CANCELLED"),
                                "description", "Trạng thái cần lọc"),
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
            return "{\"error\":\"Bạn cần đăng nhập để xem chuyển kho.\"}";
        }
        try {
            UUID transferId = optionalUuid(params, "transferId");
            if (transferId != null) {
                return objectMapper.writeValueAsString(toDetail(transferService.getTransfer(userId, transferId)));
            }
            StockTransferStatus status = optionalStatus(params);
            UUID warehouseId = resolveWarehouseId(params, context);
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 10, 30);
            List<StockTransferResponse> transfers;
            long total;
            boolean hasMore;
            if (warehouseId == null) {
                PagedResponse<StockTransferResponse> page = transferService.getTransfers(
                        userId, null, null, status, pageRequest(pageNumber, pageSize));
                transfers = page.getContent();
                total = page.getTotalElements();
                hasMore = !page.isLast();
            } else {
                int fetchSize = Math.min(100, (pageNumber + 1) * pageSize);
                PagedResponse<StockTransferResponse> outgoing = transferService.getTransfers(
                        userId, warehouseId, null, status, pageRequest(0, fetchSize));
                PagedResponse<StockTransferResponse> incoming = transferService.getTransfers(
                        userId, null, warehouseId, status, pageRequest(0, fetchSize));
                List<StockTransferResponse> combined = new ArrayList<>();
                combined.addAll(outgoing.getContent());
                combined.addAll(incoming.getContent());
                List<StockTransferResponse> sorted = combined.stream()
                        .collect(java.util.stream.Collectors.toMap(
                                StockTransferResponse::getId, value -> value, (first, ignored) -> first,
                                LinkedHashMap::new))
                        .values().stream()
                        .sorted(Comparator.comparing(StockTransferResponse::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
                int from = Math.min(pageNumber * pageSize, sorted.size());
                int to = Math.min(from + pageSize, sorted.size());
                transfers = sorted.subList(from, to);
                total = Math.max(sorted.size(), outgoing.getTotalElements() + incoming.getTotalElements());
                hasMore = to < sorted.size() || !outgoing.isLast() || !incoming.isLast();
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("warehouseName", warehouseId == null ? null : context.activeWarehouseName());
            result.put("transfers", transfers.stream().map(this::toSummary).toList());
            result.put("returned", transfers.size());
            result.put("page", pageNumber);
            result.put("total", total);
            result.put("hasMore", hasMore);
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã yêu cầu hoặc trạng thái chuyển kho không hợp lệ.\"}";
        } catch (Exception e) {
            log.warn("[GetStockTransfersTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy dữ liệu chuyển kho lúc này.\"}";
        }
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private Map<String, Object> toSummary(StockTransferResponse transfer) {
        List<StockTransferItemResponse> items = transfer.getItems() == null ? List.of() : transfer.getItems();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", transfer.getId());
        result.put("status", ChatToolLocalization.transferStatus(transfer.getStatus()));
        result.put("sourceWarehouse", transfer.getSourceWarehouse() == null ? null
                : transfer.getSourceWarehouse().getName());
        result.put("destinationWarehouse", transfer.getDestinationWarehouse() == null ? null
                : transfer.getDestinationWarehouse().getName());
        result.put("note", transfer.getNote());
        result.put("decisionReason", transfer.getDecisionReason());
        result.put("productCount", items.size());
        result.put("totalQuantity", items.stream().mapToInt(StockTransferItemResponse::getRequestedQuantity).sum());
        result.put("createdAt", transfer.getCreatedAt());
        result.put("updatedAt", transfer.getUpdatedAt());
        return result;
    }

    private Map<String, Object> toDetail(StockTransferResponse transfer) {
        Map<String, Object> result = new LinkedHashMap<>(toSummary(transfer));
        result.put("approvedAt", transfer.getApprovedAt());
        result.put("receivedAt", transfer.getReceivedAt());
        result.put("rejectedAt", transfer.getRejectedAt());
        result.put("cancelledAt", transfer.getCancelledAt());
        result.put("items", transfer.getItems() == null ? List.of()
                : transfer.getItems().stream().map(this::toItem).toList());
        return result;
    }

    private Map<String, Object> toItem(StockTransferItemResponse item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", item.getSkuCode());
        result.put("skuName", item.getSkuName());
        result.put("requestedQuantity", item.getRequestedQuantity());
        result.put("sourceAllocations", item.getSourceAllocations() == null ? List.of()
                : item.getSourceAllocations().stream().map(allocation -> {
                    Map<String, Object> location = new LinkedHashMap<>();
                    location.put("rack", allocation.getSourceRackName());
                    location.put("bin", allocation.getSourceBinName());
                    location.put("quantity", allocation.getQuantity());
                    return location;
                }).toList());
        result.put("destinationAllocations", item.getDestinationAllocations() == null ? List.of()
                : item.getDestinationAllocations().stream().map(allocation -> {
                    Map<String, Object> location = new LinkedHashMap<>();
                    location.put("rack", allocation.getDestinationRackName());
                    location.put("bin", allocation.getDestinationBinName());
                    location.put("quantity", allocation.getQuantity());
                    return location;
                }).toList());
        return result;
    }

    private StockTransferStatus optionalStatus(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("status");
        return raw == null ? null : StockTransferStatus.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
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

    /**
     * AI-supplied warehouseId takes priority over the page-context warehouse so
     * tenants can query a different warehouse without leaving the current screen.
     */
    private UUID resolveWarehouseId(Map<String, Object> params, ChatRequestContext context) {
        UUID explicit = warehouseIdFromParams(params);
        return explicit != null ? explicit
                : (context == null ? null : context.activeWarehouseId());
    }
}
