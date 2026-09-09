package fu.stockspace.stockspace_be.contract.service;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Calculates the date-aware area allocation of a Warehouse.
 *
 * <p>This service intentionally does not acquire a lock. Callers performing a
 * mutation must lock the Warehouse first and then call this service again.</p>
 */
@Service
@RequiredArgsConstructor
public class WarehouseRentalAvailabilityService {

    private final RentalContractRepository contractRepository;
    private final WarehouseLayoutService warehouseLayoutService;

    @Transactional(readOnly = true)
    public RentalAreaAvailability calculate(
            UUID warehouseId,
            UUID excludedContractId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal requestedAreaM2) {
        validateDateRange(startDate, endDate);

        WarehouseLayoutResponse defaultLayout = warehouseLayoutService
                .getDefaultLayoutForContract(warehouseId);
        BigDecimal totalAreaM2 = RentalAreaAllocationPolicy
                .calculateDefaultArea(defaultLayout);
        BigDecimal reservedAreaM2 = contractRepository.sumReservedAreaForDateRange(
                warehouseId, excludedContractId, startDate, endDate);
        if (reservedAreaM2 == null) {
            reservedAreaM2 = BigDecimal.ZERO;
        }
        return RentalAreaAvailability.of(totalAreaM2, reservedAreaM2, requestedAreaM2);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date must not be after end date");
        }
    }
}
