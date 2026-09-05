package fu.stockspace.stockspace_be.wms.stock.dto;

import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
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
public class CreateInventoryAuditPlanRequest {
    @NotNull
    private UUID warehouseId;

    @Builder.Default
    private AuditScopeType scopeType = AuditScopeType.WAREHOUSE;

    private UUID rackId;
    private UUID binId;
    private UUID assignedToId;
    private String note;
}
