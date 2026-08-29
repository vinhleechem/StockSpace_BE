package fu.stockspace.stockspace_be.wms.putaway;

import java.util.List;
import java.util.UUID;

public record PutawayAllocation(
        UUID rackId,
        UUID binId,
        int quantity,
        long score,
        List<String> reasons
) {
}
