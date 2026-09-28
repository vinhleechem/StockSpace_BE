package fu.stockspace.stockspace_be.wms.picking;

import java.util.List;
import java.util.UUID;

public record PickRouteStop(
        int sequence,
        UUID rackId,
        String rackCode,
        UUID binId,
        String binCode,
        List<PickRouteAllocation> allocations
) {
}
