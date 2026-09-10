package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferAttemptResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferEventResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptType;
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
                + "Khi đang chọn một kho, kết quả gồm cả chuyến đi và chuyến đến của kho đó. "
                + "Có thể xem thêm timeline các lần chuyển trạng thái.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "warehouseId", Map.of("type", "string",
                                "description", "UUID kho cần lọc chuyển hàng. Bỏ trống để dùng kho đang mở trên giao diện."),
                        "transferId", Map.of("type", "string", "description", "Mã yêu cầu nếu cần xem chi tiết"),
                        "includeTimeline", Map.of("type", "boolean",
                                "description", "Khi xem chi tiết, có lấy timeline xử lý hay không"),
                        "status", Map.of("type", "string",
                                "enum", List.of("DRAFT", "PENDING", "ALLOCATED", "PICKING", "READY_TO_DISPATCH",
                                        "IN_TRANSIT", "OVERDUE", "ARRIVED_AT_DESTINATION", "RECEIVING",
                                        "PARTIALLY_RECEIVED", "SHORT_RECEIVED", "RECEIVE_REJECTED", "RECONCILING",
                                        "RETRY_REQUESTED", "RETURN_REQUESTED", "RETURN_IN_TRANSIT",
                                        "PARTIALLY_RETURNED", "RETURNED", "COMPLETED", "LOST", "REJECTED", "CANCELLED"),
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
                Map<String, Object> detail = toDetail(transferService.getTransfer(userId, transferId));
                if (booleanParam(params, "includeTimeline")) {
                    detail.put("timeline", transferService.getTransferTimeline(userId, transferId)
                            .stream().map(this::toTimelineEvent).toList());
                }
                return objectMapper.writeValueAsString(detail);
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
        result.put("transferNo", transfer.getTransferNo());
        result.put("status", ChatToolLocalization.transferStatus(transfer.getStatus()));
        result.put("sourceWarehouse", transfer.getSourceWarehouse() == null ? null
                : transfer.getSourceWarehouse().getName());
        result.put("destinationWarehouse", transfer.getDestinationWarehouse() == null ? null
                : transfer.getDestinationWarehouse().getName());
        result.put("currentDestinationWarehouse", transfer.getCurrentDestinationWarehouse() == null ? null
                : transfer.getCurrentDestinationWarehouse().getName());
        result.put("sourceStaff", transfer.getSourceStaff() == null ? null
                : transfer.getSourceStaff().getFullName());
        result.put("destinationStaff", transfer.getDestinationStaff() == null ? null
                : transfer.getDestinationStaff().getFullName());
        result.put("note", transfer.getNote());
        result.put("decisionReason", transfer.getDecisionReason());
        result.put("productCount", items.size());
        result.put("totalQuantity", items.stream().mapToInt(StockTransferItemResponse::getRequestedQuantity).sum());
        result.put("shippedQuantity", items.stream().mapToInt(StockTransferItemResponse::getShippedQuantity).sum());
        result.put("receivedQuantity", items.stream().mapToInt(StockTransferItemResponse::getReceivedQuantity).sum());
        result.put("createdAt", transfer.getCreatedAt());
        result.put("updatedAt", transfer.getUpdatedAt());
        result.put("expectedArrivalAt", transfer.getExpectedArrivalAt());
        result.put("overdueAt", transfer.getOverdueAt());
        result.put("outboundReceiptId", transfer.getOutboundReceiptId());
        result.put("inboundReceiptId", transfer.getInboundReceiptId());
        return result;
    }

    private Map<String, Object> toDetail(StockTransferResponse transfer) {
        Map<String, Object> result = new LinkedHashMap<>(toSummary(transfer));
        result.put("approvedAt", transfer.getApprovedAt());
        result.put("receivedAt", transfer.getReceivedAt());
        result.put("rejectedAt", transfer.getRejectedAt());
        result.put("cancelledAt", transfer.getCancelledAt());
        result.put("attempts", transfer.getAttempts() == null ? List.of()
                : transfer.getAttempts().stream().map(this::toAttempt).toList());
        result.put("items", transfer.getItems() == null ? List.of()
                : transfer.getItems().stream().map(this::toItem).toList());
        return result;
    }

    private Map<String, Object> toTimelineEvent(StockTransferEventResponse event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromStatus", ChatToolLocalization.transferStatus(event.getFromStatus()));
        result.put("toStatus", ChatToolLocalization.transferStatus(event.getToStatus()));
        result.put("command", ChatToolLocalization.transferCommand(event.getCommand()));
        result.put("actor", event.getActor() == null ? null : event.getActor().getFullName());
        result.put("attemptSequenceNo", event.getAttemptSequenceNo());
        result.put("reason", event.getReason());
        result.put("createdAt", event.getCreatedAt());
        return result;
    }

    private Map<String, Object> toAttempt(StockTransferAttemptResponse attempt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sequenceNo", attempt.getSequenceNo());
        result.put("type", ChatToolLocalization.transferAttemptType(attempt.getType()));
        result.put("status", ChatToolLocalization.transferAttemptStatus(attempt.getStatus()));
        result.put("sourceWarehouse", attempt.getSourceWarehouse() == null ? null
                : attempt.getSourceWarehouse().getName());
        result.put("destinationWarehouse", attempt.getDestinationWarehouse() == null ? null
                : attempt.getDestinationWarehouse().getName());
        result.put("destinationStaff", attempt.getDestinationStaff() == null ? null
                : attempt.getDestinationStaff().getFullName());
        result.put("plannedQuantity", attempt.getPlannedQuantity());
        result.put("shippedQuantity", attempt.getShippedQuantity());
        result.put("receivedQuantity", attempt.getReceivedQuantity());
        result.put("reason", attempt.getReason());
        result.put("startedAt", attempt.getStartedAt());
        result.put("arrivedAt", attempt.getArrivedAt());
        result.put("completedAt", attempt.getCompletedAt());
        return result;
    }

    private Map<String, Object> toItem(StockTransferItemResponse item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", item.getSkuCode());
        result.put("skuName", item.getSkuName());
        result.put("requestedQuantity", item.getRequestedQuantity());
        result.put("reservedQuantity", item.getReservedQuantity());
        result.put("pickedQuantity", item.getPickedQuantity());
        result.put("shippedQuantity", item.getShippedQuantity());
        result.put("receivedQuantity", item.getReceivedQuantity());
        result.put("receivedGoodQuantity", item.getReceivedGoodQuantity());
        result.put("receivedDamagedQuantity", item.getReceivedDamagedQuantity());
        result.put("returnedQuantity", item.getReturnedQuantity());
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
                    location.put("disposition", allocation.getDisposition());
                    return location;
                }).toList());
        return result;
    }

    private StockTransferStatus optionalStatus(Map<String, Object> params) {
        Object raw = params == null ? null : params.get("status");
        return raw == null ? null : StockTransferStatus.valueOf(raw.toString().trim().toUpperCase(Locale.ROOT));
    }

    private boolean booleanParam(Map<String, Object> params, String key) {
        Object raw = params == null ? null : params.get(key);
        if (raw instanceof Boolean value) {
            return value;
        }
        return raw != null && "true".equalsIgnoreCase(raw.toString().trim());
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
