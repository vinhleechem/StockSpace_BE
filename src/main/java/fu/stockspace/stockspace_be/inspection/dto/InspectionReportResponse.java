package fu.stockspace.stockspace_be.inspection.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO trả về thông tin InspectionReport.
 */
@Getter
@Builder
public class InspectionReportResponse {

    private UUID id;
    private String status;
    private String checklistData;
    private String notes;
    private LocalDateTime inspectedAt;

    // Warehouse info
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    // Inspector info
    private Long inspectorId;
    private String inspectorName;

    // Owner info
    private Long ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
