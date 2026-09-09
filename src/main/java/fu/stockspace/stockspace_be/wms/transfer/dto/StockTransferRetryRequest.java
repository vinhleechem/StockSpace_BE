package fu.stockspace.stockspace_be.wms.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Future;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferRetryRequest {
    @NotNull
    private UUID destinationWarehouseId;

    @Future(message = "Expected arrival phải ở tương lai")
    private LocalDateTime expectedArrivalAt;

    @NotBlank
    @Size(max = 2000)
    private String reason;
}
