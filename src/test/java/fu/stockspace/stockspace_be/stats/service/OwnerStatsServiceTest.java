package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.stats.dto.OccupancyStatsResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.wallet.entity.Wallet;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerStatsServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private fu.stockspace.stockspace_be.wallet.service.WalletService walletService;

    @InjectMocks
    private OwnerStatsService ownerStatsService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
    }

    @Test
    void testGetRevenueSummary_Success() {
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).build();
        when(walletService.getOrCreateWallet(ownerId)).thenReturn(wallet);

        Object[] row1 = new Object[]{1, 5000000L};
        List<Object[]> monthlyData = java.util.Collections.singletonList(row1);

        when(transactionRepository.findMonthlyRevenueByWalletIdAndTypesAndYear(eq(wallet.getId()), any(), eq(2026)))
                .thenReturn(monthlyData);


        RevenueStatsResponse response = ownerStatsService.getRevenueSummary(ownerId, 2026);

        assertNotNull(response);
        assertEquals(2026, response.getYear());
        assertEquals(new BigDecimal("5000000"), response.getTotalRevenue());
        assertEquals(12, response.getMonthlyRevenue().size());
        assertEquals(new BigDecimal("5000000"), response.getMonthlyRevenue().get(0).getRevenue());
    }

    @Test
    void testGetOccupancyRate_Success() {
        Warehouse w1 = Warehouse.builder().id(UUID.randomUUID()).name("Kho Rented").status(WarehouseStatus.RENTED).build();
        Warehouse w2 = Warehouse.builder().id(UUID.randomUUID()).name("Kho Avail").status(WarehouseStatus.AVAILABLE).build();
        when(warehouseRepository.findByOwnerId(eq(ownerId), any())).thenReturn(new PageImpl<>(List.of(w1, w2)));

        OccupancyStatsResponse response = ownerStatsService.getOccupancyRate(ownerId);

        assertNotNull(response);
        assertEquals(2, response.getTotalWarehouses());
        assertEquals(1, response.getRentedWarehousesCount());
        assertEquals(1, response.getAvailableWarehousesCount());
        assertEquals(50.0, response.getOccupancyRatePercentage());
    }
}
