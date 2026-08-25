package fu.stockspace.stockspace_be.listing.dto;

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
public class PurchaseListingPackageRequest {

    @NotNull(message = "Listing package ID is required")
    private UUID listingPackageId;
}
