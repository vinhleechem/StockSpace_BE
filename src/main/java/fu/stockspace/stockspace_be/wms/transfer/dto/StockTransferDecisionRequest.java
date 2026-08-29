package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferDecisionRequest {

    @NotBlank(message = "Lý do từ chối hoặc hủy yêu cầu chuyển kho là bắt buộc")
    @Size(max = 2000, message = "Lý do không được vượt quá 2000 ký tự")
    private String reason;
}
