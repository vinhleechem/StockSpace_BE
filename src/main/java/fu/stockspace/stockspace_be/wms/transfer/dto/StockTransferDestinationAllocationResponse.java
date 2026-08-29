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
public class StockTransferDestinationAllocationResponse {
    private UUID id;
    private UUID destinationRackId;
    private String destinationRackName;
    private UUID destinationBinId;
    private String destinationBinName;
    private int quantity;
}
