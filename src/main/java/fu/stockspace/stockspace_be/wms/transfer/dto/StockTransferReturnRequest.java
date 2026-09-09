package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class StockTransferReturnRequest {
    @NotBlank
    @Size(max = 2000)
    private String reason;

    @Builder.Default
    private boolean allowPartial = false;

    @NotEmpty
    @Valid
    private List<StockTransferReturnLineRequest> lines;
}
