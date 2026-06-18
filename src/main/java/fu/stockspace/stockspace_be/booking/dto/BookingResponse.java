package fu.stockspace.stockspace_be.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * DTO trả về thông tin BookingRequest.
 */
import java.util.UUID;

@Getter
@Builder
public class BookingResponse {

    private UUID id;
    private String status;
    private BigDecimal depositAmount;
    private String rejectReason;

    // Tenant info
    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;

    // Warehouse info
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private UUID ownerId;
    private String ownerName;

    /** Phiên bản cam kết ràng buộc */
    private UUID policyId;
    private String policyVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
