package fu.stockspace.stockspace_be.wms.stock.dto;

import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditResponse {
    private UUID id;
    private UUID warehouseId;
    private String warehouseName;
    private AuditStatus status;
    private String note;


    private UUID requestedById;
    private String requestedByName;


    private UUID approvedById;
    private String approvedByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<InventoryAuditItemResponse> items;
    private int countRound;
    private AuditScopeType scopeType;
    private UUID assignedToId;
    private String assignedToName;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String reviewReason;
}
