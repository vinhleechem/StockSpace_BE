package fu.stockspace.stockspace_be.wms.putaway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Capacity observed while calculating one suggestion. The values are a
 * snapshot for display only and are not a reservation.
 */
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
