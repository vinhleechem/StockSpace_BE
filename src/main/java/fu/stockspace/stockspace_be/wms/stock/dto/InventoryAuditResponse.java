package fu.stockspace.stockspace_be.wms.stock.dto;

import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
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

    // Người yêu cầu
    private UUID requestedById;
    private String requestedByName;

    // Người duyệt (null nếu chưa duyệt)
    private UUID approvedById;
    private String approvedByName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<InventoryAuditItemResponse> items;
}
