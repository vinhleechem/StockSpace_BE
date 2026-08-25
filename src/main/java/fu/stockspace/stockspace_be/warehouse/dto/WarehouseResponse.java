package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class WarehouseResponse {

    private UUID id;
    private String name;
    private String address;
    private String description;
    private BigDecimal capacity;
    private BigDecimal rentalPrice;
    private RentalPricingType rentalPricingType;

    /** @deprecated Use rentalPrice. */
    @Deprecated
    private BigDecimal pricePerMonth;

    private String status;
    private String rejectReason;
    private boolean isVerified;
    private UUID typeId;
    private String typeName;
    private UUID ownerId;
    private String ownerName;
    private String coverImageUrl;
    private List<String> imageUrls;
    private UUID policyId;
    private String policyVersion;
    private LocalDateTime publishedAt;
    private LocalDateTime visibleUntil;
    private String publicationStatus;
    private boolean canPublish;
    private boolean canRenew;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
