package fu.stockspace.stockspace_be.wms.receipt.dto;

import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReceiptResponse {
    private UUID id;
    private UUID warehouseId;
    private String warehouseName;
    private UUID createdById;
    private String createdByFullName;
    private DocumentType type;
    private String signatureData;
    private ApprovalStatus status;
    private String rejectReason;
    private List<ReceiptItemResponse> items;
    private OutboundPickingSuggestionResponse pickList;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
