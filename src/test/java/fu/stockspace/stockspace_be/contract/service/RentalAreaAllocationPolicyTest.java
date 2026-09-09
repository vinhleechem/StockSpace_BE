package fu.stockspace.stockspace_be.contract.service;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RentalAreaAllocationPolicyTest {

    private final WarehouseLayoutResponse defaultLayout = WarehouseLayoutResponse.builder()
            .width(new BigDecimal("10"))
            .length(new BigDecimal("20"))
            .height(new BigDecimal("5"))
            .racks(List.of())
            .positions(List.of())
            .build();

    @Test
    void fixedMonthlyUsesTheCompleteDefaultLayoutAsTheAuthoritativeDimensions() {
        RentalAreaAllocationPolicy.LeasedDimensions dimensions =
                RentalAreaAllocationPolicy.resolveDimensions(
                        RentalPricingType.FIXED_MONTHLY,
                        new BigDecimal("10.0"),
                        new BigDecimal("20.00"),
                        new BigDecimal("5"),
                        defaultLayout);

        assertEquals(new BigDecimal("10"), dimensions.width());
        assertEquals(new BigDecimal("20"), dimensions.length());
        assertEquals(new BigDecimal("5"), dimensions.height());
        assertEquals(new BigDecimal("200"), dimensions.areaM2());
    }

    @Test
    void fixedMonthlyRejectsLegacyDimensionsThatDoNotMatchTheDefaultLayout() {
        assertThrows(BadRequestException.class, () ->
                RentalAreaAllocationPolicy.resolveDimensions(
                        RentalPricingType.FIXED_MONTHLY,
                        new BigDecimal("9"),
                        new BigDecimal("20"),
                        new BigDecimal("5"),
                        defaultLayout));
    }

    @Test
    void fixedMonthlyCanResolveWithoutClientDimensions() {
        RentalAreaAllocationPolicy.LeasedDimensions dimensions =
                RentalAreaAllocationPolicy.resolveDimensions(
                        RentalPricingType.FIXED_MONTHLY,
                        null,
                        null,
                        null,
                        defaultLayout);

        assertEquals(new BigDecimal("200"), dimensions.areaM2());
    }

    @Test
    void partialRentalCalculatesAreaWithoutFloatingPointLoss() {
        RentalAreaAllocationPolicy.LeasedDimensions dimensions =
                RentalAreaAllocationPolicy.resolveDimensions(
                        RentalPricingType.PER_SQUARE_METER_MONTHLY,
                        new BigDecimal("2.5"),
                        new BigDecimal("4.2"),
                        new BigDecimal("5"),
                        defaultLayout);

        assertEquals(new BigDecimal("10.50"), dimensions.areaM2());
    }

    @Test
    void partialRentalRejectsAnAreaEqualToTheWholeWarehouse() {
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
                RentalAreaAllocationPolicy.resolveDimensions(
                        RentalPricingType.NEGOTIATED,
                        new BigDecimal("10"),
                        new BigDecimal("20"),
                        new BigDecimal("5"),
                        defaultLayout));

        assertEquals("INVALID_LEASE_DIMENSIONS", exception.getErrorCode().name());
    }

    @Test
    void partialRentalRejectsDimensionsOutsideTheDefaultLayout() {
        assertThrows(BadRequestException.class, () ->
                RentalAreaAllocationPolicy.resolveDimensions(
                        RentalPricingType.PER_SQUARE_METER_MONTHLY,
                        new BigDecimal("11"),
                        new BigDecimal("1"),
                        new BigDecimal("5"),
                        defaultLayout));
    }
}
