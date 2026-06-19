package fu.stockspace.stockspace_be.inspection.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


/**
 * DTO trả về thông tin InspectionReport.
 */
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class InspectionReportResponse {

    private UUID id;
    private String status;
    private String checklistData;
    private String notes;
    private List<String> images;
    private LocalDateTime inspectedAt;

    // Warehouse info
    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    // Inspector info
    private UUID inspectorId;
    private String inspectorName;

    // Owner info
    private UUID ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
