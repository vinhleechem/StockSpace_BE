package fu.stockspace.stockspace_be.contract.service;

import java.math.BigDecimal;

/**
 * Read-only area allocation metrics for one warehouse and contract period.
 * The value is calculated on demand and is not persisted.
 */
public record RentalAreaAvailability(
        BigDecimal totalAreaM2,
        BigDecimal reservedAreaM2,
        BigDecimal availableAreaM2,
        BigDecimal requestedAreaM2,
        boolean sufficient) {

    public static RentalAreaAvailability of(
            BigDecimal totalAreaM2,
            BigDecimal reservedAreaM2,
            BigDecimal requestedAreaM2) {
        BigDecimal availableAreaM2 = totalAreaM2.subtract(reservedAreaM2);
        boolean sufficient = requestedAreaM2 != null
                && requestedAreaM2.signum() >= 0
                && requestedAreaM2.compareTo(availableAreaM2) <= 0;
        return new RentalAreaAvailability(
                totalAreaM2,
                reservedAreaM2,
                availableAreaM2,
                requestedAreaM2,
                sufficient);
    }
}
