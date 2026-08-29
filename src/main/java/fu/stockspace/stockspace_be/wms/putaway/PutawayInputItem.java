package fu.stockspace.stockspace_be.wms.putaway;

import java.util.UUID;

/**
 * Internal input for a put-away recommendation. It deliberately has no
 * persistence semantics: accepting a suggestion is a separate operation.
 */
public record PutawayInputItem(UUID skuId, int quantity) {
}
