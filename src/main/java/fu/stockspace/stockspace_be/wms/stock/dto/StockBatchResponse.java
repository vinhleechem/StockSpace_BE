package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBatchResponse {
    private UUID id;

    // SKU info
    private UUID skuId;
    private String skuCode;
    private String skuName;
    private String uomSymbol;   // sku.getUom().getSymbol()
    private String uomName;

    // Warehouse
    private UUID warehouseId;
    private String warehouseName;

    // Location
    private UUID rackId;
    private String rackName;
    private UUID binId;
    private String binName;

    private int quantity;
    private LocalDateTime arrivalDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
