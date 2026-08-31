package fu.stockspace.stockspace_be.wms.picking;

import java.util.List;

/**
 * Deterministic route ordering for already selected FIFO allocations.
 */
public record PickRoutePlan(
        List<PickRouteStop> stops,
        List<String> warnings
) {
}
