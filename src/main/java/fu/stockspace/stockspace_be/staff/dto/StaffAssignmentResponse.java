package fu.stockspace.stockspace_be.staff.dto;

import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.WarehouseRole;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffAssignmentResponse {

    private UUID id;
    private UUID staffId;
    private String staffName;
    private String staffEmail;

    private UUID tenantId;
    private String tenantName;

    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;

    private WarehouseRole role;
    private String customTitle;

    private UUID assignedById;
    private String assignedByName;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private AssignmentStatus status;
    private String notes;
}
