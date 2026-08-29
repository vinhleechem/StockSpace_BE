package fu.stockspace.stockspace_be.wms.capacity;

import java.math.BigDecimal;

public record PhysicalLoad(BigDecimal weightKg, BigDecimal volumeM3) {

    public PhysicalLoad {
        weightKg = weightKg == null ? BigDecimal.ZERO : weightKg;
        volumeM3 = volumeM3 == null ? BigDecimal.ZERO : volumeM3;
    }

    public static PhysicalLoad zero() {
        return new PhysicalLoad(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public PhysicalLoad plus(PhysicalLoad other) {
        return new PhysicalLoad(weightKg.add(other.weightKg), volumeM3.add(other.volumeM3));
    }
}
