package fu.stockspace.stockspace_be.wms.picking.dto;

import java.util.List;
import java.util.UUID;

public record OutboundPickingSuggestionResponse(
        UUID warehouseId,
        UUID layoutId,
        String strategy,
        boolean complete,
        List<OutboundPickingItemResponse> items,
        List<OutboundPickStopResponse> stops,
        List<String> warnings
) {
}
