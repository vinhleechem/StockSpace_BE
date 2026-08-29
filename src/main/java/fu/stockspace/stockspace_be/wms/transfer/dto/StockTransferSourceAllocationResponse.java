package fu.stockspace.stockspace_be.wms.transfer.dto;

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
public class StockTransferSourceAllocationResponse {
    private UUID id;
    private UUID sourceStockBatchId;
    private UUID sourceRackId;
    private String sourceRackName;
    private UUID sourceBinId;
    private String sourceBinName;
    private int quantity;
}
