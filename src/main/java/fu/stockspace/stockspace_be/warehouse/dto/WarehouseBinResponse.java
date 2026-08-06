package fu.stockspace.stockspace_be.warehouse.dto;

import lombok.*;
import java.math.BigDecimal;
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
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer positionZ;
    private Integer width;
    private Integer length;
    private Integer height;
    private boolean isOccupied;
}
