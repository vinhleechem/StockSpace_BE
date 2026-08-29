package fu.stockspace.stockspace_be.inspection.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;





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


    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;


    private UUID inspectorId;
    private String inspectorName;


    private UUID ownerId;
    private String ownerName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
