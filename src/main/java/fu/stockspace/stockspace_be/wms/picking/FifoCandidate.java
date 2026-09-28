package fu.stockspace.stockspace_be.wms.picking;

import java.time.LocalDateTime;
import java.util.UUID;

public record FifoCandidate(
        UUID stockBatchId,
        int quantity,
        LocalDateTime arrivalDate,
        LocalDateTime createdAt,
        boolean active,
        boolean deleted
) {
}
