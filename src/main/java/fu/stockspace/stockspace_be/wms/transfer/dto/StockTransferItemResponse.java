package fu.stockspace.stockspace_be.wms.transfer.dto;

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
public class StockTransferItemResponse {
    private UUID id;
    private UUID skuId;
    private String skuCode;
    private String skuName;
    private int requestedQuantity;
    private List<StockTransferSourceAllocationResponse> sourceAllocations;
    private List<StockTransferDestinationAllocationResponse> destinationAllocations;
}
