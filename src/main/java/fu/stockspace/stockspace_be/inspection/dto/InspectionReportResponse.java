package fu.stockspace.stockspace_be.inspection.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


/**
 * DTO trả về thông tin InspectionReport.
 */
@Getter
@Builder
public class InspectionReportResponse {

    private Long id;
    private String status;
    private String checklistData;
    private String notes;
    private LocalDateTime inspectedAt;

    // Warehouse info
    private Long warehouseId;
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
