package fu.stockspace.stockspace_be.wms.putaway;

import java.util.List;
import java.util.UUID;

/**
 * Complete recommendation result for one tenant warehouse.
 */
public record PutawaySuggestionResult(
        UUID warehouseId,
        UUID layoutId,
        List<PutawaySuggestionItem> items
) {
}
