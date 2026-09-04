package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;





@Slf4j
@Component
@RequiredArgsConstructor
public class GetContractDetailTool implements ChatTool {

    private final ObjectMapper objectMapper;
    private final ContractService contractService;

    @Override
    public String getName() { return "getContractDetail"; }

    @Override
    public String getDescription() {
        return "Xem chi tiết hợp đồng thuê kho của người thuê đang đăng nhập: điều khoản, tệp hợp đồng giấy, "
                + "lý do thay đổi hoặc từ chối và các lựa chọn hiện được phép.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "contractId", Map.of("type", "string", "description", "Mã hợp đồng cần xem chi tiết")
                ),
                "required", List.of("contractId")
        );
    }

    @Override
    public String execute(Map<String, Object> params, UUID userId) {
        if (userId == null) {
            return "{\"error\":\"Bạn cần đăng nhập để xem hợp đồng.\"}";
        }

        try {
            Object rawContractId = params == null ? null : params.get("contractId");
            String contractIdStr = rawContractId instanceof String value ? value.trim() : null;
            if (contractIdStr == null || contractIdStr.isBlank()) {
                return "{\"error\":\"Thiếu mã hợp đồng\"}";
            }
            UUID contractId = UUID.fromString(contractIdStr);

            RentalContractResponse contract = contractService.getContractById(contractId, userId);
            return objectMapper.writeValueAsString(toSafeContractDetail(contract));

        } catch (IllegalArgumentException e) {
            return "{\"error\":\"Mã hợp đồng không hợp lệ\"}";
        } catch (Exception e) {
            log.warn("[GetContractDetailTool] Read failed (cause={})",
                    e.getClass().getSimpleName());
            return "{\"error\":\"Không thể lấy chi tiết hợp đồng lúc này.\"}";
        }
    }

    private Map<String, Object> toSafeContractDetail(RentalContractResponse contract) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", contract.getId());
        result.put("status", ChatToolLocalization.contractStatus(contract.getStatus()));
        result.put("warehouseId", contract.getWarehouseId());
        result.put("warehouseName", contract.getWarehouseName());
        result.put("warehouseAddress", contract.getWarehouseAddress());
        result.put("startDate", contract.getStartDate());
        result.put("endDate", contract.getEndDate());
        result.put("pricingType", ChatToolLocalization.rentalPricingType(contract.getPricingType()));
        result.put("rentalPriceSnapshot", contract.getRentalPriceSnapshot());
        result.put("leasedWidth", contract.getLeasedWidth());
        result.put("leasedLength", contract.getLeasedLength());
        result.put("leasedHeight", contract.getLeasedHeight());
        result.put("leasedAreaM2", contract.getLeasedAreaM2());
        result.put("finalMonthlyRent", contract.getFinalMonthlyRent());
        result.put("paperContractFiles", contract.getPaperContractFiles());
        result.put("ownerNote", contract.getOwnerNote());
        result.put("changeRequestReason", contract.getChangeRequestReason());
        result.put("rejectionReason", contract.getRejectionReason());
        result.put("canConfirm", contract.isCanConfirm());
        result.put("canRequestChanges", contract.isCanRequestChanges());
        result.put("canReject", contract.isCanReject());
        result.put("canViewLayout", contract.isCanViewLayout());
        result.put("canManageWms", contract.isCanManageWms());
        result.put("submittedAt", contract.getSubmittedAt());
        result.put("confirmedAt", contract.getConfirmedAt());
        return result;
    }
}
