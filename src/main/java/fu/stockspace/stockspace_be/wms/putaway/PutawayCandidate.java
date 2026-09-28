package fu.stockspace.stockspace_be.wms.putaway;

import java.math.BigDecimal;
import java.util.UUID;

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
