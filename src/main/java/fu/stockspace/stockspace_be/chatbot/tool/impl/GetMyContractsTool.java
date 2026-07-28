package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tool: getMyContracts
 * Lấy danh sách hợp đồng thuê kho của Tenant hiện tại.
 *
 * ⚠️ PENDING: Chờ Dev B expose ContractService.getMyContracts(UUID tenantId)
 *    Khi Dev B xong, uncomment phần inject ContractService và gọi method đó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyContractsTool implements ChatTool {

    private final ObjectMapper objectMapper;

    // TODO: Inject ContractService khi Dev B expose getMyContracts()
    // private final ContractService contractService;

    @Override
    public String getName() { return "getMyContracts"; }

    @Override
    public String getDescription() {
        return "Lấy danh sách tất cả hợp đồng thuê kho của Tenant hiện tại: tên kho, " +
               "trạng thái hợp đồng, ngày bắt đầu, ngày kết thúc, giá thuê hàng tháng.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            // TODO: Uncomment và dùng khi Dev B expose method:
            // List<?> contracts = contractService.getMyContracts(userId);
            // return objectMapper.writeValueAsString(Map.of("contracts", contracts, "total", contracts.size()));

            // Placeholder cho đến khi Dev B expose method
            return objectMapper.writeValueAsString(Map.of(
                    "status", "pending_integration",
                    "message", "Chức năng đang được phát triển, vui lòng thử lại sau."
            ));
        } catch (Exception e) {
            log.error("[GetMyContractsTool] Error for userId {}: {}", userId, e.getMessage(), e);
            return "{\"error\": \"Không thể lấy danh sách hợp đồng lúc này.\"}";
        }
    }
}
