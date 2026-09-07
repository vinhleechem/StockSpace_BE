package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.warehouse.dto.BinSaveRequest;
import fu.stockspace.stockspace_be.warehouse.dto.RackSaveRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Provides the canonical vertical geometry for bins inside a rack.
 *
 * <p>The shelf level is the business value supplied by the caller. The
 * persisted Z coordinate is derived from that level so that clients cannot
 * accidentally create different visual and persisted layouts.</p>
 */
@Component
public class RackBinGeometryPolicy {

    private static final int GEOMETRY_SCALE = 12;
    private static final int CAPACITY_SCALE = 6;

    public BigDecimal shelfHeight(BigDecimal rackHeight, int shelfCount) {
        return rackHeight.divide(BigDecimal.valueOf(shelfCount), GEOMETRY_SCALE, RoundingMode.DOWN);
    }

    public void normalizePositionZ(BinSaveRequest bin, BigDecimal rackHeight, int shelfCount) {
        BigDecimal levelHeight = shelfHeight(rackHeight, shelfCount);
        BigDecimal levelOffset = BigDecimal.valueOf(bin.getShelfLevel() - 1L);
        bin.setPositionZ(levelHeight.multiply(levelOffset));
    }

    public boolean overlapsOnSameShelf(BinSaveRequest first, BinSaveRequest second) {
        if (!first.getShelfLevel().equals(second.getShelfLevel())) {
            return false;
        }
        return first.getCoordinateX().compareTo(second.getCoordinateX().add(second.getWidth())) < 0
                && first.getCoordinateX().add(first.getWidth()).compareTo(second.getCoordinateX()) > 0
                && first.getCoordinateY().compareTo(second.getCoordinateY().add(second.getLength())) < 0
                && first.getCoordinateY().add(first.getLength()).compareTo(second.getCoordinateY()) > 0;
    }

    public void normalizeCapacities(RackSaveRequest rack) {
        if (rack == null || rack.getShelfCount() == null || rack.getShelfCount() < 1
                || rack.getBins() == null || rack.getBins().isEmpty()) {
            return;
        }

        int shelfCount = rack.getShelfCount();
        Map<Integer, Integer> binsPerShelf = rack.getBins().stream()
                .filter(bin -> bin.getShelfLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        BinSaveRequest::getShelfLevel,
                        java.util.stream.Collectors.summingInt(bin -> 1)));

        for (BinSaveRequest bin : rack.getBins()) {
            Integer shelfLevel = bin.getShelfLevel();
            if (shelfLevel == null) {
                continue;
            }
            int binCount = binsPerShelf.getOrDefault(shelfLevel, 0);
            if (binCount < 1) {
                continue;
            }

            int divisor = shelfCount * binCount;
            bin.setMaxWeight(divideCapacity(rack.getMaxWeight(), divisor));
            bin.setMaxVolume(calculateVolumeCapacity(rack.getMaxVolume(), divisor, bin));
        }
    }

    private BigDecimal divideCapacity(BigDecimal capacity, int divisor) {
        if (capacity == null) {
            return null;
        }
        if (capacity.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return capacity.divide(BigDecimal.valueOf(divisor), CAPACITY_SCALE, RoundingMode.DOWN);
    }

    private BigDecimal calculateVolumeCapacity(BigDecimal rackVolume, int divisor, BinSaveRequest bin) {
        if (rackVolume == null) {
            return null;
        }
        if (rackVolume.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (!hasPositiveDimensions(bin)) {
            return rackVolume.divide(BigDecimal.valueOf(divisor), CAPACITY_SCALE, RoundingMode.DOWN);
        }

        BigDecimal sharedCapacity = rackVolume.divide(
                BigDecimal.valueOf(divisor), CAPACITY_SCALE, RoundingMode.DOWN);
        BigDecimal geometricVolume = bin.getWidth()
                .multiply(bin.getLength())
                .multiply(bin.getHeight());
        return sharedCapacity.min(geometricVolume)
                .setScale(CAPACITY_SCALE, RoundingMode.DOWN);
    }

    private boolean hasPositiveDimensions(BinSaveRequest bin) {
        return bin.getWidth() != null && bin.getWidth().signum() > 0
                && bin.getLength() != null && bin.getLength().signum() > 0
                && bin.getHeight() != null && bin.getHeight().signum() > 0;
    }
}
