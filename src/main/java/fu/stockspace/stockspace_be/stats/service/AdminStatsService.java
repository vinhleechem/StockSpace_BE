package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.repository.BookingRepository;
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
    private final BookingRepository bookingRepository;
    private final RentalContractRepository contractRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public PlatformSummaryResponse getPlatformSummary() {
        return PlatformSummaryResponse.builder()
                .totalUsers(userRepository.count())
                .totalWarehouses(warehouseRepository.count())
                .totalBookings(bookingRepository.count())
                .totalContracts(contractRepository.count())
                .build();
    }

    @Transactional(readOnly = true)
    public RevenueStatsResponse getMonthlyRevenue(Integer year) {
        int targetYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();

        List<Object[]> monthlyData = transactionRepository.findMonthlyRevenueByTypeAndYear(
                TransactionType.COMMISSION, targetYear);

        Map<Integer, BigDecimal> monthMap = new HashMap<>();
        BigDecimal totalCommission = BigDecimal.ZERO;

        for (Object[] row : monthlyData) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                int month = ((Number) row[0]).intValue();
                BigDecimal amount = new BigDecimal(row[1].toString());
                monthMap.put(month, amount);
                totalCommission = totalCommission.add(amount);
            }
        }

        List<MonthlyRevenueDto> monthlyList = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            monthlyList.add(new MonthlyRevenueDto(m, monthMap.getOrDefault(m, BigDecimal.ZERO)));
        }

        return RevenueStatsResponse.builder()
                .year(targetYear)
                .totalRevenue(totalCommission)
                .monthlyRevenue(monthlyList)
                .build();
    }
}
