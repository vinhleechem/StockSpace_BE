package fu.stockspace.stockspace_be.wms.putaway;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PutawaySuggestionServiceTest {

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
    private ProductSkuRepository productSkuRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;
    @Spy
    private PhysicalLoadCalculator physicalLoadCalculator;
    @Spy
    private PutawaySuggestionPlanner planner;

    @InjectMocks
    private PutawaySuggestionService suggestionService;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID layoutId;
    private UUID skuId;
    private Warehouse warehouse;
    private WarehouseLayout layout;
    private WarehouseRack rack;
    private WarehouseBin firstBin;
    private WarehouseBin secondBin;
    private ProductSku sku;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        layoutId = UUID.randomUUID();
        skuId = UUID.randomUUID();

        User tenant = User.builder().id(tenantId).build();
        warehouse = Warehouse.builder().id(warehouseId).name("Warehouse").build();
        layout = WarehouseLayout.builder().id(layoutId).warehouse(warehouse)
                .tenant(tenant).isDefault(false).build();
        rack = WarehouseRack.builder().id(UUID.randomUUID()).layout(layout)
                .name("Rack A").code("R-A")
                .maxWeight(new BigDecimal("20")).maxVolume(new BigDecimal("20"))
                .build();
        firstBin = WarehouseBin.builder().id(UUID.randomUUID()).rack(rack)
                .name("Bin A").code("B-A")
                .maxWeight(new BigDecimal("6")).maxVolume(new BigDecimal("2"))
                .build();
        secondBin = WarehouseBin.builder().id(UUID.randomUUID()).rack(rack)
                .name("Bin B").code("B-B")
                .maxWeight(new BigDecimal("4")).maxVolume(new BigDecimal("1"))
                .build();
        sku = ProductSku.builder().id(skuId).tenant(tenant).skuCode("SKU-1")
                .name("Product 1")
                .unitWeightKg(new BigDecimal("2"))
                .unitVolumeM3(new BigDecimal("0.5"))
                .build();
    }

    @Test
    void suggestUsesBothWeightAndVolumeAndSplitsAcrossBins() {
        stubReadModel();

        PutawaySuggestionResult result = suggestionService.suggest(
                tenantId, null, warehouseId, List.of(new PutawayInputItem(skuId, 5)));

        PutawaySuggestionItem item = result.items().get(0);
        assertEquals(5, item.requestedQuantity());
        assertEquals(0, item.unallocatedQuantity());
        assertEquals(2, item.allocations().size());
        assertEquals(3, item.allocations().get(0).quantity());
        assertEquals(2, item.allocations().get(1).quantity());
        assertEquals(firstBin.getId(), item.allocations().get(0).binId());
        assertEquals(secondBin.getId(), item.allocations().get(1).binId());
        assertEquals(BigDecimal.ZERO,
                item.allocations().get(0).capacity().bin().remainingWeightKg());
        assertEquals(new BigDecimal("0.5"),
                item.allocations().get(0).capacity().bin().remainingVolumeM3());
        assertNull(item.warning());
        verify(stockBatchRepository).findActivePhysicalLoadsByWarehouseIdAndTenantId(
                warehouseId, tenantId);
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void suggestReportsUnallocatedQuantityWhenAllLocationsAreFull() {
        firstBin.setMaxWeight(BigDecimal.ONE);
        secondBin.setMaxWeight(BigDecimal.ONE);
        stubReadModel();

        PutawaySuggestionItem item = suggestionService.suggest(
                        tenantId, null, warehouseId, List.of(new PutawayInputItem(skuId, 5)))
                .items().get(0);

        assertEquals(5, item.requestedQuantity());
        assertEquals(5, item.unallocatedQuantity());
        assertEquals(0, item.allocations().size());
        assertEquals("Insufficient physical capacity for the requested quantity", item.warning());
    }

    private void stubReadModel() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(Optional.of(layout));
        when(rackRepository.findAllByLayoutId(layoutId)).thenReturn(List.of(rack));
        when(binRepository.findAllByRackLayoutId(layoutId)).thenReturn(List.of(firstBin, secondBin));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(List.of());
    }
}
