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
    private UUID zoneId;
    private String name;
    private String code;
    private BigDecimal maxWeight;
    private BigDecimal maxVolume;
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer width;
    private Integer height;
    private List<WarehouseBinResponse> bins;
}
