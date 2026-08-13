package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool: getAssignedWarehouseStock
 * Xem danh sách hàng tồn kho trong các kho được phân công của Nhân viên kho (Staff).
 */
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
        return "Xem danh sách sản phẩm và số lượng hàng tồn kho của kho bãi được phân công quản lý của Nhân viên (Staff).";
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
            return "{\"error\":\"Bạn cần đăng nhập với vai trò Staff để xem tồn kho.\"}";
        }

        try {
            Optional<TenantMember> memberOpt = tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(userId);
            if (memberOpt.isEmpty()) {
                return "{\"error\":\"Bạn chưa được liên kết vào tổ chức Tenant nào.\"}";
            }

            UUID tenantId = memberOpt.get().getTenant().getId();
            UUID warehouseId = null;
            if (params != null && params.containsKey("warehouseId") && params.get("warehouseId") != null) {
                try {
                    warehouseId = UUID.fromString(params.get("warehouseId").toString());
                } catch (IllegalArgumentException ignored) {}
            }

            if (warehouseId != null) {
                List<StockBatchResponse> stock = stockBatchService
                        .getStockByWarehouse(tenantId, warehouseId, userId, PageRequest.of(0, 50))
                        .getContent();
                return objectMapper.writeValueAsString(stock);
            } else {
                return "{\"message\":\"Vui lòng cung cấp mã warehouseId để xem tồn kho chi tiết.\"}";
            }
        } catch (Exception e) {
            log.warn("[GetAssignedStockTool] Read failed (cause={})", e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy thông tin tồn kho lúc này.\"}";
        }
    }
}
