package fu.stockspace.stockspace_be.contract.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO trả về thông tin RentalContract.
 */
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
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;

    // Warehouse info
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    // Owner info
    private Long ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
