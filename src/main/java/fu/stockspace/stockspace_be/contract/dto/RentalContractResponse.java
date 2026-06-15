package fu.stockspace.stockspace_be.contract.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * DTO trả về thông tin RentalContract.
 */
@Getter
@Builder
public class RentalContractResponse {

    private Long id;
    private String status;
    private boolean tenantConfirmed;
    private boolean ownerConfirmed;
    private LocalDate startDate;
    private LocalDate endDate;
    private String paperContractImages;

    // Booking info
    private Long bookingId;
    private BigDecimal depositAmount;

    // Tenant info
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;

    // Warehouse info
    private Long warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    // Owner info
    private Long ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
