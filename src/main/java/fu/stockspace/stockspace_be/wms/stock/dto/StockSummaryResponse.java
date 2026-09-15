package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;




@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockSummaryResponse {
    private UUID skuId;
    private String skuCode;
    private String skuName;
    private String uomSymbol;
    private String uomName;
    /** Null while any location for this SKU is inside the current blind-count scope. */
    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private boolean quantityMasked;
    private List<StockLocationDto> locations;
}
