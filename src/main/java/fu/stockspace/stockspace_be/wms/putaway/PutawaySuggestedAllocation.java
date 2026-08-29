package fu.stockspace.stockspace_be.wms.putaway;

import java.util.List;
import java.util.UUID;

/**
 * One explainable allocation returned by the deterministic planner.
 */
public record PutawaySuggestedAllocation(
        UUID rackId,
        UUID binId,
        int quantity,
        long score,
        List<String> reasons,
        PutawayCapacitySnapshot capacity
) {
}
