package fu.stockspace.stockspace_be.contract.dto;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class RentalContractResponse {

    private UUID id;
    private String status;
    private boolean tenantConfirmed;
    private boolean ownerConfirmed;
    private LocalDate startDate;
    private LocalDate endDate;
    private String paperContractFiles;

    /** @deprecated Use paperContractFiles. */
    @Deprecated
    private String paperContractImages;

    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private UUID ownerId;
    private String ownerName;

    /** State- and viewer-dependent actions available to the current user. */
    private boolean canEdit;
    private boolean canDelete;
    private boolean canSubmit;
    private boolean canConfirm;
    private boolean canRequestChanges;
    private boolean canReject;
    private boolean canViewLayout;
    private boolean canManageWms;

    private RentalPricingType pricingType;
    private BigDecimal rentalPriceSnapshot;
    private BigDecimal finalMonthlyRent;
    private BigDecimal leasedWidth;
    private BigDecimal leasedLength;
    private BigDecimal leasedHeight;
    private BigDecimal leasedAreaM2;
    private String ownerNote;
    private String layoutSnapshot;
    private String changeRequestReason;
    private String rejectionReason;
    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private String cancelReason;
    private String cancelEvidence;
}
