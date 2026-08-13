package fu.stockspace.stockspace_be.wms.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Mô tả vị trí lưu kho của một lô hàng (dùng trong StockSummaryResponse).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLocationDto {
    private UUID batchId;
    private UUID warehouseId;
    private String warehouseName;
    private String rackName;
    private String binName;
    private int quantity;
}
