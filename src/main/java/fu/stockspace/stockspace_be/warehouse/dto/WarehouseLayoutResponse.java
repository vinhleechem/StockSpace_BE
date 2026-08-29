package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.*;
import java.math.BigDecimal;
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
    private BigDecimal width;
    private BigDecimal length;
    private BigDecimal height;


    private int totalRacks;
    private int totalBins;
    private int occupiedBins;
    private int emptyBins;

    private List<RackResponse> racks;


    private List<String> positions;
}
