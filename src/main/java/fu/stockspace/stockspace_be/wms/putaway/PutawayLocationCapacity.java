package fu.stockspace.stockspace_be.wms.putaway;

import java.math.BigDecimal;
import java.util.UUID;

public record PutawayLocationCapacity(
        UUID locationId,
        String name,
        BigDecimal currentWeightKg,
        BigDecimal currentVolumeM3,
        BigDecimal maxWeightKg,
        BigDecimal maxVolumeM3,
        BigDecimal remainingWeightKg,
        BigDecimal remainingVolumeM3
) {
}
