package fu.stockspace.stockspace_be.wms.capacity;

import java.math.BigDecimal;
import java.util.UUID;

public record SkuPhysicalLoad(
        UUID skuId,
        String skuCode,
        String skuName,
        long quantity,
        BigDecimal weightKg,
        BigDecimal volumeM3
) {
}
