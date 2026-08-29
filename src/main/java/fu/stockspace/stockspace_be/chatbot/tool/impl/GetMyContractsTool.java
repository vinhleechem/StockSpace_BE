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
        return "Lấy danh sách hợp đồng thuê kho của người thuê đang đăng nhập, gồm kho, trạng thái, " +
               "thời hạn, kích thước thuê và giá thuê cuối cùng. Không dùng cho khách chưa đăng nhập.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem hợp đồng.\"}";
        }

        try {
            Page<RentalContractResponse> page = contractService.getMyContractsAsTenant(userId, 0, 20);
            List<Map<String, Object>> contracts = page.getContent().stream()
                    .map(this::toSafeContractSummary)
                    .toList();

            return objectMapper.writeValueAsString(Map.of(
                    "contracts", contracts,
                    "total", page.getTotalElements()
            ));
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
        result.put("pricingType", contract.getPricingType());
        result.put("rentalPriceSnapshot", contract.getRentalPriceSnapshot());
        result.put("leasedAreaM2", contract.getLeasedAreaM2());
        result.put("finalMonthlyRent", contract.getFinalMonthlyRent());
        return result;
    }
}
