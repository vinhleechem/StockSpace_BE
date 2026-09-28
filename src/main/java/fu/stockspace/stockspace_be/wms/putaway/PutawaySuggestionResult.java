package fu.stockspace.stockspace_be.wms.putaway;

import java.util.List;
import java.util.UUID;

public record PutawaySuggestionResult(
        UUID warehouseId,
        UUID layoutId,
        List<PutawaySuggestionItem> items
) {
}
