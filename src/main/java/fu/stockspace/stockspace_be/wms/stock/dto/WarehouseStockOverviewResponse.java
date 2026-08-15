package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Product-level stock quantity for one selected warehouse.
 *
 * <p>This response intentionally contains one row per visible SKU. The
 * quantity is aggregated only from StockBatch records belonging to the
 * requested warehouse.</p>
 */
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
    private UUID warehouseId;
    private String warehouseName;
    private long totalQuantity;
}
