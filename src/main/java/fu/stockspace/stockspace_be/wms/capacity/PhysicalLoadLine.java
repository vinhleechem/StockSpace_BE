package fu.stockspace.stockspace_be.wms.capacity;

import java.math.BigDecimal;
import java.util.UUID;

public record PhysicalLoadLine(
        UUID rackId,
        UUID binId,
        UUID skuId,
        String skuCode,
        String skuName,
        BigDecimal unitWeightKg,
        BigDecimal unitVolumeM3,
        int quantity,
        boolean active,
        boolean deleted
) {

    public PhysicalLoadLine(UUID rackId, UUID binId, UUID skuId,
                            String skuCode, String skuName,
                            BigDecimal unitWeightKg, BigDecimal unitVolumeM3,
                            int quantity) {
        this(rackId, binId, skuId, skuCode, skuName,
                unitWeightKg, unitVolumeM3, quantity, true, false);
    }
}
