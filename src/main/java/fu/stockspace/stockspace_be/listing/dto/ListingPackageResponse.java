package fu.stockspace.stockspace_be.listing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Warehouse publication package; separate from rental pricing and tenant subscriptions")
public class ListingPackageResponse {

    private UUID id;
    private String name;
    private Integer durationDays;
    private BigDecimal price;
    private boolean isActive;
}
