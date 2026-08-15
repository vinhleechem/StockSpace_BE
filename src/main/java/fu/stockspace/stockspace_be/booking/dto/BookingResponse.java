package fu.stockspace.stockspace_be.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;





import java.util.UUID;

@Getter
@Builder
public class BookingResponse {

    private UUID id;
    private String status;
    private BigDecimal depositAmount;
    private String rejectReason;


    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;


    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private UUID ownerId;
    private String ownerName;


    private UUID policyId;
    private String policyVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
