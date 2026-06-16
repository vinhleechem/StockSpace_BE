package fu.stockspace.stockspace_be.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * DTO trả về thông tin BookingRequest.
 */
@Getter
@Builder
public class BookingResponse {

    private Long id;
    private String status;
    private BigDecimal depositAmount;
    private String rejectReason;

    // Tenant info
    private Long tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;

    // Warehouse info
    private Long warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private Long ownerId;
    private String ownerName;

    /** Phiên bản cam kết ràng buộc */
    private Long policyId;
    private String policyVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
