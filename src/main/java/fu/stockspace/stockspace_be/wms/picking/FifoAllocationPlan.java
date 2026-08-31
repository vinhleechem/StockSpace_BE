package fu.stockspace.stockspace_be.wms.picking;

import java.util.List;

/**
 * The deterministic result of planning one SKU quantity.
 */
public record FifoAllocationPlan(
        List<FifoAllocation> allocations,
        int shortageQuantity
) {
}
