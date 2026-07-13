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
public class SubmitAuditItemRequest {

    @NotNull(message = "batchId không được để trống")
    private UUID batchId;

    @Min(value = 0, message = "Số lượng thực tế không được âm")
    private int actualQuantity;

    private String note;
}
