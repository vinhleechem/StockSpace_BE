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
public class ZoneResponse {
    private UUID id;
    private UUID layoutId;
    private String name;
    private String code;
    private BigDecimal maxWeight;
    private BigDecimal maxVolume;
    private Integer coordinateX;
    private Integer coordinateY;
    private Integer width;
    private Integer height;
    private List<RackResponse> racks;
}
