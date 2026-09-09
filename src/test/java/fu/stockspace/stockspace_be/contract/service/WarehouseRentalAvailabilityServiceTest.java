package fu.stockspace.stockspace_be.contract.service;

import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseRentalAvailabilityServiceTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private WarehouseLayoutService warehouseLayoutService;

    @Test
    void calculatesAvailabilityFromDefaultLayoutAndReservedArea() {
        UUID warehouseId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        WarehouseLayoutResponse defaultLayout = WarehouseLayoutResponse.builder()
                .warehouseId(warehouseId)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .positions(List.of())
                .build();
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId))
                .thenReturn(defaultLayout);
        when(contractRepository.sumReservedAreaForDateRange(
                warehouseId, contractId, startDate, endDate))
                .thenReturn(new BigDecimal("75.25"));

        WarehouseRentalAvailabilityService service =
                new WarehouseRentalAvailabilityService(contractRepository, warehouseLayoutService);

        RentalAreaAvailability result = service.calculate(
                warehouseId, contractId, startDate, endDate, new BigDecimal("124.75"));

        assertEquals(new BigDecimal("200"), result.totalAreaM2());
        assertEquals(new BigDecimal("75.25"), result.reservedAreaM2());
        assertEquals(new BigDecimal("124.75"), result.availableAreaM2());
        assertEquals(new BigDecimal("124.75"), result.requestedAreaM2());
        org.junit.jupiter.api.Assertions.assertTrue(result.sufficient());
        verify(contractRepository).sumReservedAreaForDateRange(
                warehouseId, contractId, startDate, endDate);
    }

    @Test
    void includesContractIdExclusionInTheReservedAreaQuery() {
        UUID warehouseId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId))
                .thenReturn(WarehouseLayoutResponse.builder()
                        .warehouseId(warehouseId)
                        .width(new BigDecimal("10"))
                        .length(new BigDecimal("20"))
                        .height(new BigDecimal("5"))
                        .build());
        when(contractRepository.sumReservedAreaForDateRange(
                warehouseId, contractId, startDate, endDate))
                .thenReturn(BigDecimal.ZERO);

        new WarehouseRentalAvailabilityService(contractRepository, warehouseLayoutService)
                .calculate(warehouseId, contractId, startDate, endDate, new BigDecimal("20"));

        verify(contractRepository).sumReservedAreaForDateRange(
                warehouseId, contractId, startDate, endDate);
    }

    @Test
    void rejectsAnInvalidDateRangeBeforeQuerying() {
        WarehouseRentalAvailabilityService service =
                new WarehouseRentalAvailabilityService(contractRepository, warehouseLayoutService);

        assertThrows(BadRequestException.class, () -> service.calculate(
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 1),
                new BigDecimal("20")));
    }

    @Test
    void acceptsExactFitAndRejectsAnAreaOverageOfPointZeroOne() {
        UUID warehouseId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId))
                .thenReturn(WarehouseLayoutResponse.builder()
                        .warehouseId(warehouseId)
                        .width(new BigDecimal("10"))
                        .length(new BigDecimal("20"))
                        .height(new BigDecimal("5"))
                        .build());
        when(contractRepository.sumReservedAreaForDateRange(
                warehouseId, null, startDate, endDate))
                .thenReturn(new BigDecimal("100"));

        WarehouseRentalAvailabilityService service =
                new WarehouseRentalAvailabilityService(contractRepository, warehouseLayoutService);

        RentalAreaAvailability exactFit = service.calculate(
                warehouseId, null, startDate, endDate, new BigDecimal("100"));
        RentalAreaAvailability overage = service.calculate(
                warehouseId, null, startDate, endDate, new BigDecimal("100.01"));

        assertTrue(exactFit.sufficient());
        assertEquals(new BigDecimal("100"), exactFit.availableAreaM2());
        assertFalse(overage.sufficient());
    }

    @Test
    void reportsUnavailableWhenExistingReservationsAlreadyExceedTheArea() {
        UUID warehouseId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 10, 31);
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId))
                .thenReturn(WarehouseLayoutResponse.builder()
                        .warehouseId(warehouseId)
                        .width(new BigDecimal("10"))
                        .length(new BigDecimal("20"))
                        .height(new BigDecimal("5"))
                        .build());
        when(contractRepository.sumReservedAreaForDateRange(
                warehouseId, null, startDate, endDate))
                .thenReturn(new BigDecimal("200.01"));

        RentalAreaAvailability result = new WarehouseRentalAvailabilityService(
                contractRepository, warehouseLayoutService)
                .calculate(warehouseId, null, startDate, endDate, BigDecimal.ZERO);

        assertEquals(new BigDecimal("-0.01"), result.availableAreaM2());
        assertFalse(result.sufficient());
    }
}
