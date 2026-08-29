package fu.stockspace.stockspace_be.wms.putaway;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A candidate location with the quantity that can currently fit there.
 * Capacity values are calculated by the put-away service before planning.
 */
public record PutawayCandidate(
        UUID rackId,
        UUID binId,
        String rackCode,
        String binCode,
        BigDecimal positionZ,
        boolean containsSku,
        int maxQuantity,
        BigDecimal remainingCapacityRatio
) {
}
