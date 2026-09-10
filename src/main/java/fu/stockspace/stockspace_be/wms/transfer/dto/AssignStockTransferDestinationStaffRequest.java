package fu.stockspace.stockspace_be.wms.transfer.dto;

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
public class AssignStockTransferDestinationStaffRequest {

    @NotNull(message = "destinationStaffId is required")
    private UUID destinationStaffId;

    @Size(max = 2000, message = "reason must not exceed 2000 characters")
    private String reason;
}
