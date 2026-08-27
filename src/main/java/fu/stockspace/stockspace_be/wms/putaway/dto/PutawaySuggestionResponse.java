package fu.stockspace.stockspace_be.wms.putaway.dto;

import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionItem;

import java.util.List;
import java.util.UUID;

public record PutawaySuggestionResponse(
        UUID warehouseId,
        UUID layoutId,
        PutawayContext context,
        List<PutawaySuggestionItem> items
) {
}
