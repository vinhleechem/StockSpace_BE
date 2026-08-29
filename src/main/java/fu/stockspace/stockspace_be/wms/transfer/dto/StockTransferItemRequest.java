package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferItemRequest {

    @NotNull(message = "Mã SKU không được để trống")
    private UUID skuId;

    @Min(value = 1, message = "Số lượng chuyển phải lớn hơn 0")
    private int requestedQuantity;

    @NotEmpty(message = "Phải có ít nhất một phân bổ nguồn cho SKU")
    @Valid
    private List<StockTransferSourceAllocationRequest> sourceAllocations;
}
