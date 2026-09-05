package fu.stockspace.stockspace_be.wms.stock.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAuditCountItemRequest {
    @NotNull
    private UUID itemId;

    @NotNull
    @Min(0)
    private Integer actualQuantity;

    private String note;
    private String varianceReason;
}
