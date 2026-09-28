package fu.stockspace.stockspace_be.wms.picking;

import java.util.UUID;

public record PickRouteAllocation(
        UUID skuId,
        UUID stockBatchId,
        int quantity
) {
}
