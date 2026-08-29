package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@Schema(description = "Warehouse details without private owner contact fields")
public class WarehouseResponse {

    private UUID id;
    private String name;
    private String address;
    private String provinceCode;
    private String provinceName;
    private String districtCode;
    private String districtName;
    private String description;
    private BigDecimal capacity;
    @Schema(description = "Listing price source; distinct from a contract's final monthly rent")
    private BigDecimal rentalPrice;
    @Schema(allowableValues = {"FIXED_MONTHLY", "PER_SQUARE_METER_MONTHLY", "NEGOTIATED"})
    private RentalPricingType rentalPricingType;

    @Schema(allowableValues = {"AVAILABLE", "PENDING_APPROVAL", "INACTIVE"})
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
    @Schema(description = "Current publication state", allowableValues = {"DRAFT", "PENDING_APPROVAL", "PUBLISHED", "EXPIRED", "REFUNDED"})
    private String publicationStatus;
    private boolean canPublish;
    private boolean canRenew;
    @Schema(description = "Latest listing order ID for owner/admin views")
    private UUID currentListingOrderId;
    @Schema(description = "Latest listing order status for owner/admin views", allowableValues = {"PENDING_APPROVAL", "ACTIVATED", "REFUNDED"})
    private ListingOrderStatus currentListingOrderStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
