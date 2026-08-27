package fu.stockspace.stockspace_be.staff.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only operation projection used by the Staff portal.
 * Mutations remain on the owning WMS modules.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffOperationResponse {

    private String operationType;
    private UUID operationId;
    private UUID warehouseId;
    private String warehouseName;
    private UUID sourceWarehouseId;
    private String sourceWarehouseName;
    private UUID destinationWarehouseId;
    private String destinationWarehouseName;
    private String status;
    private LocalDateTime createdAt;
    private List<String> allowedActions;
}
