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
public class RackResponse {
    private UUID id;
    private UUID layoutId;
    private String name;
    private String code;
    private BigDecimal maxWeight;
    private BigDecimal maxVolume;
    private BigDecimal coordinateX;
    private BigDecimal coordinateY;
    private BigDecimal positionZ;
    private Integer rotation;
    private BigDecimal width;
    private BigDecimal length;
    private BigDecimal height;
    private Integer shelfCount;
    private List<String> occupiedPositions;
    private List<WarehouseBinResponse> bins;
}
