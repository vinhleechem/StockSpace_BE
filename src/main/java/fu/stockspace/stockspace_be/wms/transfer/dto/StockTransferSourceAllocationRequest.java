package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferSourceAllocationRequest {

    @NotNull(message = "Stock batch nguồn không được để trống")
    private UUID sourceStockBatchId;

    @NotNull(message = "Rack nguồn không được để trống")
    private UUID sourceRackId;

    @NotNull(message = "Bin nguồn không được để trống")
    private UUID sourceBinId;

    @Min(value = 1, message = "Số lượng phân bổ phải lớn hơn 0")
    private int quantity;
}
