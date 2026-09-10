package fu.stockspace.stockspace_be.stats.service;

import fu.stockspace.stockspace_be.common.config.BusinessTimeConfig;
import fu.stockspace.stockspace_be.stats.dto.OccupancyStatsResponse;
import fu.stockspace.stockspace_be.stats.dto.RevenueStatsResponse;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerStatsServiceTest {

    @Mock private WarehouseRepository warehouseRepository;
    @Mock private RentalContractRepository contractRepository;
    @Mock private Clock businessClock;

    @InjectMocks
    private OwnerStatsService ownerStatsService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(businessClock.instant())
                .thenReturn(Instant.parse("2026-12-31T17:00:00Z"));
        org.mockito.Mockito.lenient().when(businessClock.getZone())
                .thenReturn(BusinessTimeConfig.BUSINESS_ZONE_ID);
    }

    @Test
    void testGetRevenueSummary_Success() {
        RevenueStatsResponse response = ownerStatsService.getRevenueSummary(ownerId, 2026);

        assertNotNull(response);
        assertEquals(2026, response.getYear());
        assertEquals(BigDecimal.ZERO, response.getTotalRevenue());
        assertEquals(12, response.getMonthlyRevenue().size());
        assertEquals(BigDecimal.ZERO, response.getMonthlyRevenue().get(0).getRevenue());
    }

    @Test
    void testGetRevenueSummary_UsesHoChiMinhBusinessYearWhenYearIsOmitted() {
        RevenueStatsResponse response = ownerStatsService.getRevenueSummary(ownerId, null);

        assertEquals(2027, response.getYear());
    }

    @Test
    void testGetOccupancyRate_Success() {
        Warehouse w1 = Warehouse.builder().id(UUID.randomUUID()).name("Kho A").build();
        Warehouse w2 = Warehouse.builder().id(UUID.randomUUID()).name("Kho B").build();
        when(warehouseRepository.findByOwnerId(eq(ownerId), any())).thenReturn(new PageImpl<>(List.of(w1, w2)));
        when(contractRepository.findCurrentDirectActiveWarehouseIdsByOwnerId(eq(ownerId), any()))
                .thenReturn(List.of(w1.getId()));
        when(contractRepository.countCurrentDirectActiveContractsByOwnerId(eq(ownerId), any())).thenReturn(2L);
        when(contractRepository.countDistinctCurrentDirectActiveTenantsByOwnerId(eq(ownerId), any())).thenReturn(2L);

        OccupancyStatsResponse response = ownerStatsService.getOccupancyRate(ownerId);

        assertNotNull(response);
        assertEquals(2, response.getTotalWarehouses());
        assertEquals(1, response.getWarehousesWithActiveContracts());
        assertEquals(2, response.getActiveContractCount());
        assertEquals(2, response.getActiveTenantCount());
        assertEquals(50.0, response.getOccupancyRatePercentage());
    }
}
