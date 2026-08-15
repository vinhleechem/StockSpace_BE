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
public class WarehouseBinResponse {
    private UUID id;
    private UUID rackId;
    private String name;
    private String code;
    private BigDecimal maxWeight;
    private BigDecimal maxVolume;
    private Integer shelfLevel;
    private BigDecimal coordinateX;
    private BigDecimal coordinateY;
    private BigDecimal positionZ;
    private BigDecimal width;
    private BigDecimal length;
    private BigDecimal height;
    private boolean isOccupied;
    private List<String> occupiedPositions;
}
