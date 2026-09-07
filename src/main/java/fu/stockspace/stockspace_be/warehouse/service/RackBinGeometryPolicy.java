package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.warehouse.dto.BinSaveRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
}
