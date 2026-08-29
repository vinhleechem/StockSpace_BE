package fu.stockspace.stockspace_be.wms.capacity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseLayoutCapacityResponse {
    private UUID warehouseId;
    private String warehouseName;
    private UUID layoutId;
    private List<RackCapacityResponse> racks;
}
