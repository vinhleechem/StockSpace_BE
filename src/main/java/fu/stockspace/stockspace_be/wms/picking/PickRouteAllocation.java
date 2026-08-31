package fu.stockspace.stockspace_be.wms.picking;

import java.util.UUID;

/**
 * One FIFO allocation to be picked at a route stop.
 */
public record PickRouteAllocation(
        UUID skuId,
        UUID stockBatchId,
        int quantity
) {
}
