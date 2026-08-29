package fu.stockspace.stockspace_be.wms.putaway;

import java.util.List;

public record PutawayPlan(
        List<PutawayAllocation> allocations,
        int unallocatedQuantity
) {
}
