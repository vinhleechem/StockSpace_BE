package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.stats.dto.MonthlyRevenueDto;
import fu.stockspace.stockspace_be.stats.dto.OccupancyStatsResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.entity.Wallet;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
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

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public RevenueStatsResponse getRevenueSummary(UUID ownerId, Integer year) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();

        Wallet wallet = walletService.getOrCreateWallet(ownerId);


        List<TransactionType> revenueTypes = List.of(
                TransactionType.DEPOSIT_RECEIVED,
                TransactionType.DEPOSIT_PAYMENT
        );
        List<Object[]> monthlyData = transactionRepository.findMonthlyRevenueByWalletIdAndTypesAndYear(
                wallet.getId(), revenueTypes, targetYear);

        Map<Integer, BigDecimal> monthMap = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] row : monthlyData) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                int month = ((Number) row[0]).intValue();
                BigDecimal amount = new BigDecimal(row[1].toString());
                monthMap.merge(month, amount, BigDecimal::add);
                total = total.add(amount);
            }
        }

        List<MonthlyRevenueDto> monthlyList = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyList.add(new MonthlyRevenueDto(m, monthMap.getOrDefault(m, BigDecimal.ZERO)));
        }

        return RevenueStatsResponse.builder()
                .year(targetYear)
                .totalRevenue(total)
                .monthlyRevenue(monthlyList)
                .build();
    }

    @Transactional(readOnly = true)
    public OccupancyStatsResponse getOccupancyRate(UUID ownerId) {
        List<Warehouse> warehouses = warehouseRepository.findByOwnerId(ownerId, Pageable.unpaged()).getContent();
        int total = warehouses.size();
        int rented = 0;
        List<String> rentedNames = new ArrayList<>();
        List<String> availableNames = new ArrayList<>();

        for (Warehouse w : warehouses) {
            if (w.getStatus() == WarehouseStatus.RENTED) {
                rented++;
                rentedNames.add(w.getName());
            } else if (w.getStatus() == WarehouseStatus.AVAILABLE) {
                availableNames.add(w.getName());
            }
        }

        double rate = total > 0 ? ((double) rented / total) * 100.0 : 0.0;

        return OccupancyStatsResponse.builder()
                .totalWarehouses(total)
                .rentedWarehousesCount(rented)
                .availableWarehousesCount(availableNames.size())
                .occupancyRatePercentage(Math.round(rate * 100.0) / 100.0)
                .rentedWarehouseNames(rentedNames)
                .availableWarehouseNames(availableNames)
                .build();
    }
}
