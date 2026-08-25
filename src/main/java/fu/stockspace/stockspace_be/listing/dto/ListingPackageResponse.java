package fu.stockspace.stockspace_be.listing.dto;

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
public class ListingPackageResponse {

    private UUID id;
    private String name;
    private Integer durationDays;
    private BigDecimal price;
    private boolean isActive;
}
