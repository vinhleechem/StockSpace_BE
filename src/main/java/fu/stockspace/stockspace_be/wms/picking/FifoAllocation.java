package fu.stockspace.stockspace_be.wms.picking;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record FifoAllocation(
        UUID stockBatchId,
        int quantity,
        LocalDateTime arrivalDate,
        List<String> reasons
) {
}
