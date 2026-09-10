package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.config.BusinessTimeConfig;

import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.stats.dto.PlatformSummaryResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private RentalContractRepository contractRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private Clock businessClock;


    @InjectMocks
    private AdminStatsService adminStatsService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(businessClock.instant())
                .thenReturn(Instant.parse("2026-12-31T17:00:00Z"));
        org.mockito.Mockito.lenient().when(businessClock.getZone())
                .thenReturn(BusinessTimeConfig.BUSINESS_ZONE_ID);
    }

    @Test
    void testGetPlatformSummary_Success() {
        when(userRepository.count()).thenReturn(100L);
        when(warehouseRepository.count()).thenReturn(20L);
        when(contractRepository.countDirectContractsByStatus()).thenReturn(List.of(
                new Object[]{ContractStatus.ACTIVE, 7L},
                new Object[]{ContractStatus.DRAFT, 3L}));

        PlatformSummaryResponse response = adminStatsService.getPlatformSummary();

        assertNotNull(response);
        assertEquals(100L, response.getTotalUsers());
        assertEquals(20L, response.getTotalWarehouses());
        assertEquals(10L, response.getTotalContracts());
        assertEquals(7L, response.getContractCountsByStatus().get("ACTIVE"));
    }

    @Test
    void testGetMonthlyRevenue_Success() {
        Object[] row1 = new Object[]{3, 1500000L};
        List<Object[]> monthlyData = java.util.Collections.singletonList(row1);

        when(transactionRepository.findMonthlyRevenueByTypeAndYear(
                fu.stockspace.stockspace_be.wallet.entity.TransactionType.LISTING_FEE, 2026))
                .thenReturn(monthlyData);
        when(transactionRepository.findMonthlyRevenueByTypeAndYear(
                fu.stockspace.stockspace_be.wallet.entity.TransactionType.PACKAGE_PAYMENT, 2026))
                .thenReturn(java.util.Collections.singletonList(new Object[]{3, 500000L}));


        RevenueStatsResponse response = adminStatsService.getMonthlyRevenue(2026);

        assertNotNull(response);
        assertEquals(2026, response.getYear());
        assertEquals(new BigDecimal("2000000"), response.getTotalRevenue());
        assertEquals(new BigDecimal("1500000"), response.getListingFeeRevenue());
        assertEquals(new BigDecimal("500000"), response.getServicePackageRevenue());
        assertEquals(12, response.getMonthlyRevenue().size());
        assertEquals(new BigDecimal("2000000"), response.getMonthlyRevenue().get(2).getRevenue());
    }

    @Test
    void testGetMonthlyRevenue_UsesHoChiMinhBusinessYearWhenYearIsOmitted() {
        when(transactionRepository.findMonthlyRevenueByTypeAndYear(
                fu.stockspace.stockspace_be.wallet.entity.TransactionType.LISTING_FEE, 2027))
                .thenReturn(List.of());
        when(transactionRepository.findMonthlyRevenueByTypeAndYear(
                fu.stockspace.stockspace_be.wallet.entity.TransactionType.PACKAGE_PAYMENT, 2027))
                .thenReturn(List.of());

        RevenueStatsResponse response = adminStatsService.getMonthlyRevenue(null);

        assertEquals(2027, response.getYear());
    }
}
