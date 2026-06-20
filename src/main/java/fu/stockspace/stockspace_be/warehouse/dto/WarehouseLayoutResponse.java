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
    private Integer height;
    private List<ZoneResponse> zones;
}
