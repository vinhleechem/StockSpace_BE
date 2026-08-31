package fu.stockspace.stockspace_be.wms.picking;

import java.util.UUID;

/**
 * Internal representation of one requested outbound SKU quantity.
 */
public record OutboundPickingInputItem(
        UUID skuId,
        int quantity
) {
}
