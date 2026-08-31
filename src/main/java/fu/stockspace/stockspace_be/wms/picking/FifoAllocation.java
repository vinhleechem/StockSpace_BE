package fu.stockspace.stockspace_be.wms.picking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One quantity allocation from a stock batch selected by FIFO.
 */
public record FifoAllocation(
        UUID stockBatchId,
        int quantity,
        LocalDateTime arrivalDate,
        List<String> reasons
) {
}
