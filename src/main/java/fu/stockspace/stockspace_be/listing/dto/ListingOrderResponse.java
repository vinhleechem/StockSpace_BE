package fu.stockspace.stockspace_be.listing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingOrderResponse {

    private UUID id;
    private UUID warehouseId;
    private UUID listingPackageId;
    private String listingPackageName;
    private UUID transactionId;
    private Integer durationDays;
    private BigDecimal price;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime createdAt;
}
