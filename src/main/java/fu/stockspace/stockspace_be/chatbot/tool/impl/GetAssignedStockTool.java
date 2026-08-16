package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetAssignedStockTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final TenantMemberRepository tenantMemberRepository;
    private final StockBatchService stockBatchService;

    @Override
    public String getName() {
        return "getAssignedWarehouseStock";
    }

    @Override
    public String getDescription() {
        return "Xem danh sách sản phẩm và số lượng hàng tồn kho của kho đang được chọn và được phân công cho Nhân viên (Staff).";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        return readAssignedStock(userId, warehouseIdFromParams(params));
    }

    @Override
    public String executeWithContext(Map<String, Object> params, ChatRequestContext context) {
        return readAssignedStock(
                context == null ? null : context.userId(),
                context == null ? null : context.activeWarehouseId());
    }

    private String readAssignedStock(UUID userId, UUID warehouseId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Staff để xem tồn kho.\"}";
        }
        if (warehouseId == null) {
            return "{\"error\":\"Chưa có kho được chọn. Vui lòng chọn kho trên giao diện rồi thử lại.\"}";
        }

        try {
            Optional<TenantMember> memberOpt = tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(userId);
            if (memberOpt.isEmpty()) {
                return "{\"error\":\"Bạn chưa được liên kết vào tổ chức Tenant nào.\"}";
            }

            UUID tenantId = memberOpt.get().getTenant().getId();
            List<StockBatchResponse> stock = stockBatchService
                    .getStockByWarehouse(tenantId, warehouseId, userId, PageRequest.of(0, 50))
                    .getContent();
            return objectMapper.writeValueAsString(stock.stream()
                    .map(this::toSafeStock)
                    .toList());
        } catch (Exception e) {
            log.warn("[GetAssignedStockTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin tồn kho lúc này.\"}";
        }
    }

    private Map<String, Object> toSafeStock(StockBatchResponse stock) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", stock.getSkuCode());
        result.put("skuName", stock.getSkuName());
        result.put("unit", stock.getUomSymbol());
        result.put("warehouseName", stock.getWarehouseName());
        result.put("rackName", stock.getRackName());
        result.put("binName", stock.getBinName());
        result.put("quantity", stock.getQuantity());
        result.put("arrivalDate", stock.getArrivalDate());
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
