package fu.stockspace.stockspace_be.wms.transfer.dto;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReturnDisposition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class StockTransferReturnLineRequest {
    @NotNull
    private UUID itemId;
    @Min(1)
    private int quantity;
    @NotNull
    private UUID sourceRackId;
    @NotNull
    private UUID sourceBinId;

    /** Condition accepted back into the source warehouse. */
    @Builder.Default
    private StockTransferReturnDisposition disposition = StockTransferReturnDisposition.GOOD;

    /** Required when the returned quantity is not accepted as usable stock. */
    @Size(max = 2000)
    private String note;
}
