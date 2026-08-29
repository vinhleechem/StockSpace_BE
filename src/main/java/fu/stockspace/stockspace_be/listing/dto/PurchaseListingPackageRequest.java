package fu.stockspace.stockspace_be.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Purchase or renewal request for a warehouse publication")
public class PurchaseListingPackageRequest {

    @Schema(format = "uuid")
    @NotNull(message = "Listing package ID is required")
    private UUID listingPackageId;
}
