package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditCountStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditItemOrigin;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditItemResponse {
    private UUID id;
    private UUID batchId;
    private UUID skuId;
    private String skuCode;
    private String skuName;
    private String uomSymbol;
    private UUID rackId;
    private String rackName;
    private UUID binId;
    private String binName;
    private AuditItemOrigin itemOrigin;
    private Integer expectedQuantity;
    private Integer actualQuantity;
    private Integer discrepancy;
    private String note;
    private String varianceReason;
    private LocalDateTime arrivalDate;
    private AuditCountStatus countStatus;
    private UUID countedById;
    private LocalDateTime countedAt;
    private int countRound;
}
