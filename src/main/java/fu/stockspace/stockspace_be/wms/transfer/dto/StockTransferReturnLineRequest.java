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
public class StockTransferReturnLineRequest {
    @NotNull
    private UUID itemId;
    @Min(1)
    private int quantity;
    @NotNull
    private UUID sourceRackId;
    @NotNull
    private UUID sourceBinId;
}
