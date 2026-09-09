package fu.stockspace.stockspace_be.wms.transfer.dto;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReconciliationResolution;
import jakarta.validation.constraints.NotNull;
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
public class StockTransferReconcileRequest {
    @NotNull
    private StockTransferReconciliationResolution resolution;

    @Size(max = 2000)
    private String reason;
}
