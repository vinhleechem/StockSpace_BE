package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.stats.dto.MonthlyRevenueDto;
import fu.stockspace.stockspace_be.stats.dto.PlatformSummaryResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final RentalContractRepository contractRepository;
    private final TransactionRepository transactionRepository;


    @Transactional(readOnly = true)
    public PlatformSummaryResponse getPlatformSummary() {
        Map<String, Long> contractCounts = new LinkedHashMap<>();
        for (Object[] row : contractRepository.countDirectContractsByStatus()) {
            if (row != null && row.length >= 2 && row[0] instanceof ContractStatus status
                    && row[1] instanceof Number count) {
                contractCounts.put(status.name(), count.longValue());
            }
        }
        return PlatformSummaryResponse.builder()
                .totalUsers(userRepository.count())
                .totalWarehouses(warehouseRepository.count())
                .totalContracts(contractCounts.values().stream().mapToLong(Long::longValue).sum())
                .contractCountsByStatus(contractCounts)
                .build();
    }

    @Transactional(readOnly = true)
    public RevenueStatsResponse getMonthlyRevenue(Integer year) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();

        Map<Integer, BigDecimal> monthMap = new HashMap<>();
        BigDecimal listingFeeRevenue = mergeMonthlyRevenue(
                transactionRepository.findMonthlyRevenueByTypeAndYear(TransactionType.LISTING_FEE, targetYear),
                monthMap);
        BigDecimal servicePackageRevenue = mergeMonthlyRevenue(
                transactionRepository.findMonthlyRevenueByTypeAndYear(TransactionType.PACKAGE_PAYMENT, targetYear),
                monthMap);

        List<MonthlyRevenueDto> monthlyList = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyList.add(new MonthlyRevenueDto(m, monthMap.getOrDefault(m, BigDecimal.ZERO)));
        }

        return RevenueStatsResponse.builder()
                .year(targetYear)
                .totalRevenue(listingFeeRevenue.add(servicePackageRevenue))
                .listingFeeRevenue(listingFeeRevenue)
                .servicePackageRevenue(servicePackageRevenue)
                .monthlyRevenue(monthlyList)
                .build();
    }

    private BigDecimal mergeMonthlyRevenue(List<Object[]> rows, Map<Integer, BigDecimal> monthMap) {
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] instanceof Number monthValue && row[1] != null) {
                int month = monthValue.intValue();
                BigDecimal amount = new BigDecimal(row[1].toString());
                monthMap.merge(month, amount, BigDecimal::add);
                total = total.add(amount);
            }
        }
        return total;
    }
}
