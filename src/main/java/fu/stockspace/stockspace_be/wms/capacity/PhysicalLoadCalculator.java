package fu.stockspace.stockspace_be.wms.capacity;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for converting SKU quantities to physical load.
 * Values are calculated with BigDecimal and are never persisted as counters.
 */
@Component
public class PhysicalLoadCalculator {

    public PhysicalLoad calculate(Collection<PhysicalLoadLine> lines,
                                  boolean requireWeight,
                                  boolean requireVolume) {
        BigDecimal weight = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;

        if (lines == null) {
            return PhysicalLoad.zero();
        }

        for (PhysicalLoadLine line : lines) {
            if (line == null || !line.active() || line.deleted() || line.quantity() <= 0) {
                continue;
            }
            validatePhysicalProperties(line, requireWeight, requireVolume);
            BigDecimal quantity = BigDecimal.valueOf(line.quantity());
            if (requireWeight) {
                weight = weight.add(line.unitWeightKg().multiply(quantity));
            }
            if (requireVolume) {
                volume = volume.add(line.unitVolumeM3().multiply(quantity));
            }
        }
        return new PhysicalLoad(weight, volume);
    }

    public List<SkuPhysicalLoad> summarizeBySku(Collection<PhysicalLoadLine> lines) {
        Map<UUID, SkuAccumulator> grouped = new LinkedHashMap<>();
        if (lines == null) {
            return List.of();
        }

        for (PhysicalLoadLine line : lines) {
            if (line == null || !line.active() || line.deleted() || line.quantity() <= 0) {
                continue;
            }
            validatePhysicalProperties(line, true, true);
            SkuAccumulator accumulator = grouped.computeIfAbsent(line.skuId(), ignored ->
                    new SkuAccumulator(line.skuId(), line.skuCode(), line.skuName()));
            BigDecimal quantity = BigDecimal.valueOf(line.quantity());
            accumulator.quantity += line.quantity();
            accumulator.weightKg = accumulator.weightKg.add(line.unitWeightKg().multiply(quantity));
            accumulator.volumeM3 = accumulator.volumeM3.add(line.unitVolumeM3().multiply(quantity));
        }

        return grouped.values().stream()
                .map(SkuAccumulator::toRecord)
                .toList();
    }

    public boolean isLimited(BigDecimal capacity) {
        return capacity != null && capacity.signum() > 0;
    }

    public void assertWithinCapacity(String type, String name,
                                     BigDecimal maxWeight,
                                     BigDecimal maxVolume,
                                     PhysicalLoad total) {
        if (isLimited(maxWeight) && total.weightKg().compareTo(maxWeight) > 0) {
            throw new BadRequestException("Physical weight capacity exceeded for " + type + " " + name
                    + " (limit=" + maxWeight + " kg, requested=" + total.weightKg() + " kg)");
        }
        if (isLimited(maxVolume) && total.volumeM3().compareTo(maxVolume) > 0) {
            throw new BadRequestException("Physical volume capacity exceeded for " + type + " " + name
                    + " (limit=" + maxVolume + " m3, requested=" + total.volumeM3() + " m3)");
        }
    }

    private void validatePhysicalProperties(PhysicalLoadLine line,
                                             boolean requireWeight,
                                             boolean requireVolume) {
        if (requireWeight && !hasPositive(line.unitWeightKg())) {
            throw new BadRequestException("SKU " + line.skuCode()
                    + " is missing unitWeightKg; capacity-limited inbound is not allowed");
        }
        if (requireVolume && !hasPositive(line.unitVolumeM3())) {
            throw new BadRequestException("SKU " + line.skuCode()
                    + " is missing unitVolumeM3; capacity-limited inbound is not allowed");
        }
    }

    private boolean hasPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static final class SkuAccumulator {
        private final UUID skuId;
        private final String skuCode;
        private final String skuName;
        private long quantity;
        private BigDecimal weightKg = BigDecimal.ZERO;
        private BigDecimal volumeM3 = BigDecimal.ZERO;

        private SkuAccumulator(UUID skuId, String skuCode, String skuName) {
            this.skuId = skuId;
            this.skuCode = skuCode;
            this.skuName = skuName;
        }

        private SkuPhysicalLoad toRecord() {
            return new SkuPhysicalLoad(skuId, skuCode, skuName, quantity, weightKg, volumeM3);
        }
    }
}
