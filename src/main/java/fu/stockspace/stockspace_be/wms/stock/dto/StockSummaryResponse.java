package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Tổng hợp tồn kho theo SKU (kèm danh sách vị trí phân tán).
 */
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
    private int totalQuantity;
    private List<StockLocationDto> locations;
}
