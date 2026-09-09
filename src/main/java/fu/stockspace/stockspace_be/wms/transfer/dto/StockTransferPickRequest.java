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
public class StockTransferPickRequest {
    @NotEmpty
    @Valid
    private List<StockTransferPickLineRequest> lines;
}
