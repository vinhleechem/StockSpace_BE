package fu.stockspace.stockspace_be.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupancyStatsResponse {
    private int totalWarehouses;
    private int warehousesWithActiveContracts;
    private long activeContractCount;
    private long activeTenantCount;
    private double occupancyRatePercentage;
    private List<String> occupiedWarehouseNames;
}
