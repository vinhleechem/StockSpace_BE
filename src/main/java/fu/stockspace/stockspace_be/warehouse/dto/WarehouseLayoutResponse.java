package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseLayoutResponse {
    private UUID id;
    private UUID warehouseId;
    private UUID tenantId;
    private boolean isDefault;
    private Integer width;
    private Integer length;
    private Integer height;

    // Layout Statistics
    private int totalRacks;
    private int totalBins;
    private int occupiedBins;
    private int emptyBins;

    private List<RackResponse> racks;
}
