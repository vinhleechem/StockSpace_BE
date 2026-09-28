package fu.stockspace.stockspace_be.wms.putaway;

import java.util.UUID;

public record PutawayInputItem(UUID skuId, int quantity) {
}
