package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.stats.dto.MonthlyRevenueDto;
import fu.stockspace.stockspace_be.stats.dto.OccupancyStatsResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerStatsService {

    private final WarehouseRepository warehouseRepository;
    private final RentalContractRepository contractRepository;

    @Transactional(readOnly = true)
    public RevenueStatsResponse getRevenueSummary(UUID ownerId, Integer year) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();

        List<MonthlyRevenueDto> monthlyList = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyList.add(new MonthlyRevenueDto(m, BigDecimal.ZERO));
        }

        // Rental payments are settled outside StockSpace and are not platform revenue.
        return RevenueStatsResponse.builder()
                .year(targetYear)
                .totalRevenue(BigDecimal.ZERO)
                .listingFeeRevenue(BigDecimal.ZERO)
                .servicePackageRevenue(BigDecimal.ZERO)
                .monthlyRevenue(monthlyList)
                .build();
    }

    @Transactional(readOnly = true)
    public OccupancyStatsResponse getOccupancyRate(UUID ownerId) {
        List<Warehouse> warehouses = warehouseRepository.findByOwnerId(ownerId, Pageable.unpaged()).getContent();
        int total = warehouses.size();
        LocalDate today = LocalDate.now();
        Set<UUID> occupiedWarehouseIds = new HashSet<>(
                contractRepository.findCurrentDirectActiveWarehouseIdsByOwnerId(ownerId, today));
        List<String> occupiedNames = warehouses.stream()
                .filter(warehouse -> occupiedWarehouseIds.contains(warehouse.getId()))
                .map(Warehouse::getName)
                .toList();
        double rate = total > 0 ? ((double) occupiedWarehouseIds.size() / total) * 100.0 : 0.0;

        return OccupancyStatsResponse.builder()
                .totalWarehouses(total)
                .warehousesWithActiveContracts(occupiedWarehouseIds.size())
                .activeContractCount(contractRepository.countCurrentDirectActiveContractsByOwnerId(ownerId, today))
                .activeTenantCount(contractRepository.countDistinctCurrentDirectActiveTenantsByOwnerId(ownerId, today))
                .occupancyRatePercentage(Math.round(rate * 100.0) / 100.0)
                .occupiedWarehouseNames(occupiedNames)
                .build();
    }
}
