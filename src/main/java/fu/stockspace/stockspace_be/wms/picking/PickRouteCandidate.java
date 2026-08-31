package fu.stockspace.stockspace_be.wms.picking;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A FIFO allocation enriched with the physical location data needed for
 * route ordering.
 */
public record PickRouteCandidate(
        UUID skuId,
        UUID stockBatchId,
        UUID rackId,
        String rackCode,
        BigDecimal rackCoordinateX,
        BigDecimal rackCoordinateY,
        UUID binId,
        String binCode,
        BigDecimal binCoordinateX,
        Integer shelfLevel,
        int quantity
) {
}
