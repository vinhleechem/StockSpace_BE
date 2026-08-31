package fu.stockspace.stockspace_be.wms.picking;

import java.util.List;
import java.util.UUID;

/**
 * One physical bin visited by the picker. All allocations for that bin are
 * kept together so the picker does not return to the same location.
 */
public record PickRouteStop(
        int sequence,
        UUID rackId,
        String rackCode,
        UUID binId,
        String binCode,
        List<PickRouteAllocation> allocations
) {
}
