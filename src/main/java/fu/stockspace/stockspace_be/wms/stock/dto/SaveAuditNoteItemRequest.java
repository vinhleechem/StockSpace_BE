package fu.stockspace.stockspace_be.wms.stock.dto;

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
public class SaveAuditNoteItemRequest {
    @NotNull
    private UUID itemId;

    private String note;
    private String varianceReason;
}
