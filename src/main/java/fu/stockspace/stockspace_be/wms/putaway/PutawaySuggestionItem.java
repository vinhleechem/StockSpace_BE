package fu.stockspace.stockspace_be.wms.putaway;

import java.util.List;
import java.util.UUID;

/**
 * Recommendation result for one requested SKU.
 */
public record PutawaySuggestionItem(
        UUID skuId,
        String skuCode,
        String skuName,
        int requestedQuantity,
        List<PutawaySuggestedAllocation> allocations,
        int unallocatedQuantity,
        String warning
) {
}
