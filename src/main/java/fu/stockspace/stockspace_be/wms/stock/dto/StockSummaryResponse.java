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
    /** When true, quantity fields are placeholders and must not be displayed as numbers. */
    private int totalQuantity;
    private int reservedQuantity;
    private int availableQuantity;
    private boolean quantityMasked;
    private List<StockLocationDto> locations;
}
