package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiveStockTransferRequest {

    @Builder.Default
    private boolean allowPartial = false;

    @NotEmpty(message = "Destination allocations must not be empty")
    @Valid
    private List<StockTransferDestinationAllocationRequest> destinationAllocations;
}
