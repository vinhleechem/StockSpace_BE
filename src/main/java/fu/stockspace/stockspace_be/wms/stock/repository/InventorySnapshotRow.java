package fu.stockspace.stockspace_be.wms.stock.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventorySnapshotRow(
        UUID batchId,
        UUID skuId,
        String skuCode,
        String skuName,
        String categoryName,
        String uomCode,
        String uomName,
        UUID warehouseId,
        String warehouseName,
        UUID rackId,
        String rackName,
        String rackCode,
        UUID binId,
        String binName,
        String binCode,
        Integer shelfLevel,
        int quantity,
        LocalDateTime arrivalDate,
        BigDecimal unitWeightKg,
        BigDecimal unitVolumeM3
) {
}
