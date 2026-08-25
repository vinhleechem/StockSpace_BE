package fu.stockspace.stockspace_be.contract.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DisputeResponse {

    private UUID id;
    private String status;
    private String reason;
    private String evidenceImages;
    private String adminNote;

    private UUID contractId;

    // Kho bãi
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    // Tiền cọc & Thời hạn thuê
    private BigDecimal depositAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String paperContractFiles;

    /** @deprecated Use paperContractFiles. */
    @Deprecated
    private String paperContractImages;

    // Thông tin Tenant
    private UUID tenantId;
    private String tenantName;
    private String tenantEmail;
    private String tenantPhone;

    // Thông tin Owner
    private UUID ownerId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;

    // Thông tin hủy ban đầu (nếu có)
    private String cancelReason;
    private String cancelEvidence;

    // Người tạo & Người xử lý tranh chấp
    private UUID raisedById;
    private String raisedByName;

    private UUID handledById;
    private String handledByName;

    private LocalDateTime createdAt;
}
