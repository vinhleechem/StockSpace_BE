package fu.stockspace.stockspace_be.wms.picking;

import java.util.List;

public record FifoAllocationPlan(
        List<FifoAllocation> allocations,
        int shortageQuantity
) {
}
