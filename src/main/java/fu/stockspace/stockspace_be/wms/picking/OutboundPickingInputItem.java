package fu.stockspace.stockspace_be.wms.picking;

import java.util.UUID;

public record OutboundPickingInputItem(
        UUID skuId,
        int quantity
) {
}
