package fu.stockspace.stockspace_be.wms.capacity.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.capacity.CapacityStatus;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadLine;
import fu.stockspace.stockspace_be.wms.capacity.dto.BinCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.RackCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.SkuCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.dto.WarehouseLayoutCapacityResponse;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseCapacityServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private WarehouseLayoutRepository layoutRepository;
    @Mock
    private WarehouseRackRepository rackRepository;
    @Mock
    private WarehouseBinRepository binRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;
    @Spy
    private PhysicalLoadCalculator physicalLoadCalculator;

    @InjectMocks
    private WarehouseCapacityService capacityService;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID layoutId;
    private UUID rackId;
    private UUID binId;
    private Warehouse warehouse;
    private WarehouseLayout layout;
    private WarehouseRack rack;
    private WarehouseBin bin;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        layoutId = UUID.randomUUID();
        rackId = UUID.randomUUID();
        binId = UUID.randomUUID();

        warehouse = Warehouse.builder().id(warehouseId).name("Main Warehouse").build();
        layout = WarehouseLayout.builder()
                .id(layoutId)
                .warehouse(warehouse)
                .tenant(User.builder().id(tenantId).build())
                .isDefault(false)
                .build();
        rack = WarehouseRack.builder()
                .id(rackId).layout(layout).name("Rack A").code("R_A")
                .build();
        bin = WarehouseBin.builder()
                .id(binId).rack(rack).name("Bin A").code("B_A")
                .build();
    }

    @Test
    void getCapacity_ContractOnly_AllowsReadWithoutSubscription() {
        stubReadModel(List.of());

        WarehouseLayoutCapacityResponse response = capacityService.getCapacity(tenantId, warehouseId, null);

        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(CapacityStatus.EMPTY, response.getRacks().get(0).getCapacityStatus());
        verify(accessService).requireActiveContract(tenantId, warehouseId);
        verify(accessService, never()).requireActiveSubscription(any());
    }

    @Test
    void getCapacity_StaffRequiresActiveAssignment() {
        UUID staffId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        doNothing().when(accessService).requireActiveContract(tenantId, warehouseId);
        doNothing().when(accessService).requireActiveStaffAssignment(staffId, tenantId, warehouseId);
        stubLayoutAndLocations();
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(List.of());

        capacityService.getCapacity(tenantId, warehouseId, staffId);

        verify(accessService).requireActiveStaffAssignment(staffId, tenantId, warehouseId);
    }

    @Test
    void getCapacity_StaffWithoutActiveAssignment_IsForbidden() {
        UUID staffId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        doNothing().when(accessService).requireActiveContract(tenantId, warehouseId);
        doThrow(new ForbiddenException(ErrorCode.FORBIDDEN))
                .when(accessService).requireActiveStaffAssignment(staffId, tenantId, warehouseId);

        assertThrows(ForbiddenException.class,
                () -> capacityService.getCapacity(tenantId, warehouseId, staffId));
        verify(layoutRepository, never()).findByWarehouseIdAndTenantId(any(), any());
        verify(stockBatchRepository, never())
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(any(), any());
    }

    @Test
    void getCapacity_ExpiredContract_IsForbiddenBeforeLoadingLayout() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        doThrow(new ForbiddenException(ErrorCode.FORBIDDEN))
                .when(accessService).requireActiveContract(tenantId, warehouseId);

        assertThrows(ForbiddenException.class,
                () -> capacityService.getCapacity(tenantId, warehouseId, null));
        verify(layoutRepository, never()).findByWarehouseIdAndTenantId(any(), any());
        verify(stockBatchRepository, never())
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(any(), any());
    }

    @Test
    void getCapacity_GroupsMultipleBatchesBySkuAndUsesOneStockQuery() {
        rack.setMaxWeight(new BigDecimal("20"));
        rack.setMaxVolume(new BigDecimal("10"));
        bin.setMaxWeight(new BigDecimal("20"));
        bin.setMaxVolume(new BigDecimal("10"));
        PhysicalLoadLine firstSkuBatch = line("SKU-A", "Product A", 2,
                new BigDecimal("1"), new BigDecimal("0.25"));
        PhysicalLoadLine secondSkuBatch = line("SKU-A", "Product A", 3,
                new BigDecimal("1"), new BigDecimal("0.25"));
        PhysicalLoadLine otherSkuBatch = line("SKU-B", "Product B", 1,
                new BigDecimal("2"), new BigDecimal("0.5"));
        stubReadModel(List.of(firstSkuBatch, secondSkuBatch, otherSkuBatch));

        WarehouseLayoutCapacityResponse response = capacityService.getCapacity(tenantId, warehouseId, null);

        RackCapacityResponse rackResponse = response.getRacks().get(0);
        assertEquals(new BigDecimal("7"), rackResponse.getCurrentWeightKg());
        assertEquals(new BigDecimal("1.75"), rackResponse.getCurrentVolumeM3());
        assertEquals(CapacityStatus.AVAILABLE, rackResponse.getCapacityStatus());
        assertEquals(2, rackResponse.getStoredSkus().size());
        SkuCapacityResponse groupedSku = rackResponse.getStoredSkus().get(0);
        assertEquals("SKU-A", groupedSku.getSkuCode());
        assertEquals(5, groupedSku.getQuantity());
        assertEquals(new BigDecimal("5"), groupedSku.getWeightKg());
        assertEquals(new BigDecimal("1.25"), groupedSku.getVolumeM3());

        BinCapacityResponse binResponse = rackResponse.getBins().get(0);
        assertEquals(2, binResponse.getStoredSkus().size());
        verify(stockBatchRepository, times(1))
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId);
        verify(stockBatchRepository, never()).findByRackId(any());
        verify(stockBatchRepository, never()).findByBinId(any());
    }

    @Test
    void getCapacity_ReturnsRoundedUtilizationAndNullForUnlimited() {
        rack.setMaxWeight(new BigDecimal("3"));
        rack.setMaxVolume(BigDecimal.ZERO);
        bin.setMaxWeight(BigDecimal.ONE);
        bin.setMaxVolume(null);
        stubReadModel(List.of(line("SKU-A", "Product A", 1,
                BigDecimal.ONE, new BigDecimal("0.25"))));

        WarehouseLayoutCapacityResponse response = capacityService.getCapacity(tenantId, warehouseId, null);

        RackCapacityResponse rackResponse = response.getRacks().get(0);
        assertEquals(new BigDecimal("33.33"), rackResponse.getWeightUtilizationPercent());
        assertEquals(new BigDecimal("2"), rackResponse.getRemainingWeightKg());
        assertNull(rackResponse.getVolumeUtilizationPercent());
        assertNull(rackResponse.getRemainingVolumeM3());

        BinCapacityResponse binResponse = rackResponse.getBins().get(0);
        assertEquals(CapacityStatus.FULL, binResponse.getCapacityStatus());
        assertNull(binResponse.getVolumeUtilizationPercent());
    }

    @Test
    void getCapacity_ReportsOverCapacityAndNegativeRemaining() {
        rack.setMaxWeight(BigDecimal.ONE);
        bin.setMaxWeight(BigDecimal.ONE);
        stubReadModel(List.of(line("SKU-A", "Product A", 2,
                BigDecimal.ONE, BigDecimal.ONE)));

        WarehouseLayoutCapacityResponse response = capacityService.getCapacity(tenantId, warehouseId, null);

        assertEquals(CapacityStatus.OVER_CAPACITY, response.getRacks().get(0).getCapacityStatus());
        assertEquals(new BigDecimal("-1"), response.getRacks().get(0).getRemainingWeightKg());
        assertEquals(CapacityStatus.OVER_CAPACITY,
                response.getRacks().get(0).getBins().get(0).getCapacityStatus());
    }

    @Test
    void getCapacity_PassesTenantScopeToLayoutAndStockQueries() {
        stubReadModel(List.of());

        capacityService.getCapacity(tenantId, warehouseId, null);

        verify(layoutRepository).findByWarehouseIdAndTenantId(warehouseId, tenantId);
        verify(stockBatchRepository).findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId);
    }

    private void stubReadModel(List<PhysicalLoadLine> lines) {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        doNothing().when(accessService).requireActiveContract(tenantId, warehouseId);
        stubLayoutAndLocations();
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(lines);
    }

    private void stubLayoutAndLocations() {
        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(Optional.of(layout));
        when(rackRepository.findAllByLayoutId(layoutId)).thenReturn(List.of(rack));
        when(binRepository.findAllByRackLayoutId(layoutId)).thenReturn(List.of(bin));
    }

    private PhysicalLoadLine line(String skuCode, String skuName, int quantity,
                                  BigDecimal unitWeightKg, BigDecimal unitVolumeM3) {
        UUID skuId = UUID.nameUUIDFromBytes(skuCode.getBytes(StandardCharsets.UTF_8));
        return new PhysicalLoadLine(rackId, binId, skuId, skuCode, skuName,
                unitWeightKg, unitVolumeM3, quantity);
    }
}
