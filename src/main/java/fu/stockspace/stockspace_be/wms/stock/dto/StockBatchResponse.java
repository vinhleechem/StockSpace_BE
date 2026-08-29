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


    private UUID skuId;
    private String skuCode;
    private String skuName;
    private String uomSymbol;
    private String uomName;


    private UUID warehouseId;
    private String warehouseName;


    private UUID rackId;
    private String rackName;
    private UUID binId;
    private String binName;

    private int quantity;
    private LocalDateTime arrivalDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
