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
    private String zoneName;
    private String zoneCode;
    private String name;
    private String code;
    private BigDecimal maxWeight;
    private BigDecimal maxVolume;
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer positionZ;
    private Integer rotation;
    private Integer width;
    private Integer length;
    private Integer height;
    private List<WarehouseBinResponse> bins;
}
