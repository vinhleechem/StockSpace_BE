package fu.stockspace.stockspace_be.contract.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * DTO trả về thông tin RentalContract.
 */
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
    private String paperContractImages;

    // Booking info
    private UUID bookingId;
    private BigDecimal depositAmount;

    // Tenant info
    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;

    // Warehouse info
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    // Owner info
    private UUID ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Phase 1 deal info
    private LocalDateTime submittedAt;
    private String cancelReason;
    private String cancelEvidence;
}
