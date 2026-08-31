package fu.stockspace.stockspace_be.wms.picking;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A stock batch candidate prepared by the application service before FIFO
 * planning. The planner only reads this value object and never changes stock.
 */
public record FifoCandidate(
        UUID stockBatchId,
        int quantity,
        LocalDateTime arrivalDate,
        LocalDateTime createdAt,
        boolean active,
        boolean deleted
) {
}
