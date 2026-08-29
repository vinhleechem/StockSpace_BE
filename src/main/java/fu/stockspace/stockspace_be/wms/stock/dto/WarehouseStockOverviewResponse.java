package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;








@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseStockOverviewResponse {

    private UUID skuId;
    private String skuCode;
    private String skuName;
    private UUID categoryId;
    private String categoryName;
    private String uomSymbol;
    private String uomName;
    private BigDecimal unitWeightKg;
    private BigDecimal unitVolumeM3;
    private UUID warehouseId;
    private String warehouseName;
    private long totalQuantity;
    private BigDecimal totalWeightKg;
    private BigDecimal totalVolumeM3;
}
