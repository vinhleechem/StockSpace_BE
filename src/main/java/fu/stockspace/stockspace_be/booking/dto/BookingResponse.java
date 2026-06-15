package fu.stockspace.stockspace_be.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO trả về thông tin BookingRequest.
 */
@Getter
@Builder
public class BookingResponse {

    private UUID id;
    private String status;
    private BigDecimal depositAmount;
    private String rejectReason;

    // Tenant info
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;

    // Warehouse info
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private Long ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
