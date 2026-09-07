package fu.stockspace.stockspace_be.wms.capacity;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicalLoadCalculatorTest {

    private final PhysicalLoadCalculator calculator = new PhysicalLoadCalculator();

    @Test
    void calculatesWeightWithoutVolumeWhenOnlyWeightIsLimited() {
        PhysicalLoadLine line = line(new BigDecimal("2.5"), null);

        PhysicalLoad load = assertDoesNotThrow(() ->
                calculator.calculate(List.of(line), true, false));

        assertEquals(new BigDecimal("5.0"), load.weightKg());
        assertEquals(BigDecimal.ZERO, load.volumeM3());
    }

    @Test
    void calculatesVolumeWithoutWeightWhenOnlyVolumeIsLimited() {
        PhysicalLoadLine line = line(null, new BigDecimal("0.25"));

        PhysicalLoad load = assertDoesNotThrow(() ->
                calculator.calculate(List.of(line), false, true));

        assertEquals(BigDecimal.ZERO, load.weightKg());
        assertEquals(new BigDecimal("0.50"), load.volumeM3());
    }

    @Test
    void rejectsMissingMetadataOnlyForTheLimitedDimension() {
        PhysicalLoadLine line = line(null, new BigDecimal("0.25"));

        assertThrows(BadRequestException.class,
                () -> calculator.calculate(List.of(line), true, false));
        assertDoesNotThrow(() -> calculator.calculate(List.of(line), false, true));
    }

    private PhysicalLoadLine line(BigDecimal unitWeightKg, BigDecimal unitVolumeM3) {
        return new PhysicalLoadLine(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "SKU-1", "Product 1", unitWeightKg, unitVolumeM3, 2);
    }
}
