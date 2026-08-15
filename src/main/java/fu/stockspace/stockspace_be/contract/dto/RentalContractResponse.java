package fu.stockspace.stockspace_be.contract.dto;

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
    private String paperContractImages;


    private UUID bookingId;
    private BigDecimal depositAmount;


    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;


    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;


    private UUID ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    private LocalDateTime submittedAt;
    private String cancelReason;
    private String cancelEvidence;
}
