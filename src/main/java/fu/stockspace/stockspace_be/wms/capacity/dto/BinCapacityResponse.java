package fu.stockspace.stockspace_be.wms.capacity.dto;

import fu.stockspace.stockspace_be.wms.capacity.CapacityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinCapacityResponse {
    private UUID binId;
    private String binName;
    private BigDecimal currentWeightKg;
    private BigDecimal currentVolumeM3;
    private BigDecimal maxWeightKg;
    private BigDecimal maxVolumeM3;
    private BigDecimal remainingWeightKg;
    private BigDecimal remainingVolumeM3;
    private BigDecimal weightUtilizationPercent;
    private BigDecimal volumeUtilizationPercent;
    private CapacityStatus capacityStatus;
    private List<SkuCapacityResponse> storedSkus;
}
