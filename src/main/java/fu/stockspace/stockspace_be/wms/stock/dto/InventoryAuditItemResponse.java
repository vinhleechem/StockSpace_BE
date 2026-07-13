package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAuditItemResponse {
    private UUID id;
    private UUID batchId;
    private String skuCode;
    private String skuName;
    private String uomSymbol;
    private String zoneName;
    private String rackName;
    private String binName;
    private int expectedQuantity;
    private Integer actualQuantity;
    private Integer discrepancy;
    private String note;
}
