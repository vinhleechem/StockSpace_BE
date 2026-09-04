package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetMyContractsTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final ContractService contractService;

    @Override
    public String getName() { return "getMyContracts"; }

    @Override
    public String getDescription() {
        return "Lấy danh sách hợp đồng thuê kho của người thuê đang đăng nhập, gồm trạng thái, thời hạn, "
                + "giá thuê cuối cùng và các lựa chọn hiện được phép như xác nhận, yêu cầu sửa hoặc từ chối.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of(
                "page", Map.of("type", "integer", "minimum", 0),
                "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 30)));
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem hợp đồng.\"}";
        }

        try {
            int pageNumber = ChatToolParameters.page(params);
            int pageSize = ChatToolParameters.pageSize(params, 10, 30);
            Page<RentalContractResponse> page = contractService.getMyContractsAsTenant(
                    userId, pageNumber, pageSize);
            List<Map<String, Object>> contracts = page.getContent().stream()
                    .map(this::toSafeContractSummary)
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contracts", contracts);
            result.put("page", page.getNumber());
            result.put("total", page.getTotalElements());
            result.put("totalPages", page.getTotalPages());
            result.put("hasMore", !page.isLast());
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Thông tin phân trang hợp đồng không hợp lệ.\"}";
        } catch (Exception e) {
            log.warn("[GetMyContractsTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy danh sách hợp đồng lúc này.\"}";
        }
    }

    private Map<String, Object> toSafeContractSummary(RentalContractResponse contract) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", contract.getId());
        result.put("status", ChatToolLocalization.contractStatus(contract.getStatus()));
        result.put("warehouseId", contract.getWarehouseId());
        result.put("warehouseName", contract.getWarehouseName());
        result.put("startDate", contract.getStartDate());
        result.put("endDate", contract.getEndDate());
        result.put("pricingType", ChatToolLocalization.rentalPricingType(contract.getPricingType()));
        result.put("rentalPriceSnapshot", contract.getRentalPriceSnapshot());
        result.put("leasedAreaM2", contract.getLeasedAreaM2());
        result.put("finalMonthlyRent", contract.getFinalMonthlyRent());
        result.put("canConfirm", contract.isCanConfirm());
        result.put("canRequestChanges", contract.isCanRequestChanges());
        result.put("canReject", contract.isCanReject());
        result.put("canViewLayout", contract.isCanViewLayout());
        result.put("canManageWms", contract.isCanManageWms());
        return result;
    }
}
