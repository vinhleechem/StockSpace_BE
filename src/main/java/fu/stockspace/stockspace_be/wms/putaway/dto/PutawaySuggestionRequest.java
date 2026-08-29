package fu.stockspace.stockspace_be.wms.putaway.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PutawaySuggestionRequest {

    @NotNull(message = "Warehouse id is required")
    private UUID warehouseId;

    @NotEmpty(message = "At least one SKU item is required")
    @Valid
    private List<PutawaySuggestionItemRequest> items;

    @NotNull(message = "Put-away context is required")
    private PutawayContext context;
}
