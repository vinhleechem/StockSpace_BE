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
 * Tool: getContractDetail
 * Xem chi tiết một hợp đồng cụ thể của Tenant.
 *
 * ⚠️ PENDING: Chờ Dev B expose ContractService.getContractDetail(UUID contractId, UUID userId)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GetContractDetailTool implements ChatTool {

    private final ObjectMapper objectMapper;

    // TODO: Inject ContractService khi Dev B expose getContractDetail()
    // private final ContractService contractService;

    @Override
    public String getName() { return "getContractDetail"; }

    @Override
    public String getDescription() {
        return "Xem chi tiết một hợp đồng thuê kho cụ thể: thông tin kho, Tenant, Owner, " +
               "ngày bắt đầu/kết thúc, điều khoản, lịch sử thanh toán.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "contractId", Map.of("type", "STRING", "description", "ID của hợp đồng cần xem chi tiết")
                ),
                "required", List.of("contractId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            String contractIdStr = (String) params.get("contractId");
            if (contractIdStr == null || contractIdStr.isBlank()) {
                return "{\"error\": \"Thiếu contractId\"}";
            }
            UUID contractId = UUID.fromString(contractIdStr);

            // TODO: Uncomment khi Dev B expose method:
            // Object detail = contractService.getContractDetail(contractId, userId);
            // return objectMapper.writeValueAsString(detail);

            return objectMapper.writeValueAsString(Map.of(
                    "status", "pending_integration",
                    "contractId", contractIdStr,
                    "message", "Chức năng đang được phát triển, vui lòng thử lại sau."
            ));

        } catch (IllegalArgumentException e) {
            return "{\"error\": \"contractId không hợp lệ\"}";
        } catch (Exception e) {
            log.error("[GetContractDetailTool] Error: {}", e.getMessage(), e);
            return "{\"error\": \"Không thể lấy chi tiết hợp đồng lúc này.\"}";
        }
    }
}
