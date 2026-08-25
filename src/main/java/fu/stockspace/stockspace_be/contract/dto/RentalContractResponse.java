package fu.stockspace.stockspace_be.contract.dto;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@Schema(description = "Direct rental contract plus viewer-specific action flags")
public class RentalContractResponse {

    private UUID id;
    @Schema(allowableValues = {"DRAFT", "PENDING_TENANT_CONFIRM", "CHANGES_REQUESTED", "ACTIVE", "REJECTED", "EXPIRED"})
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    @Schema(description = "Paper-contract file URLs")
    private List<String> paperContractFiles;

    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private UUID ownerId;
    private String ownerName;

    /** State- and viewer-dependent actions available to the current user. */
    @Schema(description = "Current viewer may edit contract terms")
    private boolean canEdit;
    @Schema(description = "Current viewer may soft-delete this draft")
    private boolean canDelete;
    @Schema(description = "Current viewer may submit or resubmit this contract")
    private boolean canSubmit;
    @Schema(description = "Current viewer may confirm this submitted contract")
    private boolean canConfirm;
    @Schema(description = "Current viewer may request owner changes")
    private boolean canRequestChanges;
    @Schema(description = "Current viewer may reject this submitted contract")
    private boolean canReject;
    @Schema(description = "Current viewer may read the contract layout")
    private boolean canViewLayout;
    @Schema(description = "True only for the contract tenant when the contract and subscription are both ACTIVE")
    private boolean canManageWms;

    @Schema(allowableValues = {"FIXED_MONTHLY", "PER_SQUARE_METER_MONTHLY", "NEGOTIATED"})
    private RentalPricingType pricingType;
    @Schema(description = "Warehouse price captured when the draft was computed; null for NEGOTIATED pricing")
    private BigDecimal rentalPriceSnapshot;
    @Schema(description = "Final monthly rent frozen in the contract")
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
}
