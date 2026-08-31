package fu.stockspace.stockspace_be.wms.picking.dto;

import java.util.UUID;

public record OutboundPickingItemResponse(
        UUID skuId,
        int requestedQuantity,
        int allocatedQuantity,
        int shortageQuantity
) {
}
