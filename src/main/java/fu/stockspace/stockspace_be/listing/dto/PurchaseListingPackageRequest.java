package fu.stockspace.stockspace_be.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to schedule a warehouse publication")
public class PurchaseListingPackageRequest {

    @Schema(format = "uuid")
    @NotNull(message = "Listing package ID is required")
    private UUID listingPackageId;

    @Schema(
            description = "Publication start date in Asia/Ho_Chi_Minh; today or a future date",
            example = "2026-09-01",
            format = "date"
    )
    @NotNull(message = "Publication start date is required")
    private LocalDate startDate;

    /**
     * Keeps existing internal callers source-compatible until the publication
     * service starts requiring the schedule date.
     *
     * @deprecated use {@link #PurchaseListingPackageRequest(UUID, LocalDate)}
     */
    @Deprecated
    public PurchaseListingPackageRequest(UUID listingPackageId) {
        this(listingPackageId, null);
    }
}
