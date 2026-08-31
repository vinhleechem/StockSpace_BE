package fu.stockspace.stockspace_be.wms.picking;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboundPickingSuggestionServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID WAREHOUSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000702");
    private static final UUID LAYOUT_ID = UUID.fromString("00000000-0000-0000-0000-000000000703");
    private static final UUID SKU_ID = UUID.fromString("00000000-0000-0000-0000-000000000704");
    private static final UUID BATCH_1_ID = UUID.fromString("00000000-0000-0000-0000-000000000705");
    private static final UUID BATCH_2_ID = UUID.fromString("00000000-0000-0000-0000-000000000706");

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private WarehouseLayoutRepository layoutRepository;
    @Mock
    private ProductSkuRepository productSkuRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Spy
    private FifoAllocationPlanner fifoPlanner;
    @Spy
    private SerpentineRoutePlanner routePlanner;

    @InjectMocks
    private OutboundPickingSuggestionService suggestionService;

    private Warehouse warehouse;
    private WarehouseLayout layout;
    private WarehouseRack rack;
    private WarehouseBin bin;
    private ProductSku sku;
    private User tenant;

    @BeforeEach
    void setUp() {
        tenant = User.builder().id(TENANT_ID).build();
        warehouse = Warehouse.builder().id(WAREHOUSE_ID).name("Warehouse").build();
        layout = WarehouseLayout.builder().id(LAYOUT_ID).warehouse(warehouse)
                .tenant(tenant).isDefault(false).build();
        rack = WarehouseRack.builder().id(UUID.randomUUID()).layout(layout)
                .name("Rack A").code("RACK-A")
                .coordinateX(java.math.BigDecimal.ONE)
                .coordinateY(java.math.BigDecimal.ONE)
                .build();
        bin = WarehouseBin.builder().id(UUID.randomUUID()).rack(rack)
                .name("Bin A").code("BIN-A")
                .coordinateX(java.math.BigDecimal.ONE)
                .shelfLevel(1)
                .build();
        sku = ProductSku.builder().id(SKU_ID).tenant(tenant)
                .skuCode("SKU-001").name("Product 1").build();
    }

    @Test
    void suggestForTenantReturnsFifoRouteWithoutPersistingAnything() {
        StockBatch oldBatch = batch(BATCH_1_ID, 10, LocalDateTime.of(2026, 8, 1, 8, 0));
        StockBatch newBatch = batch(BATCH_2_ID, 10, LocalDateTime.of(2026, 8, 2, 8, 0));
        stubValidRequest(List.of(oldBatch, newBatch));

        OutboundPickingSuggestionResponse response = suggestionService.suggest(
                TENANT_ID, null, WAREHOUSE_ID, List.of(new OutboundPickingInputItem(SKU_ID, 15)));

        assertEquals(OutboundPickingSuggestionService.STRATEGY, response.strategy());
        assertTrue(response.complete());
        assertEquals(15, response.items().get(0).allocatedQuantity());
        assertEquals(0, response.items().get(0).shortageQuantity());
        assertEquals(1, response.stops().size());
        assertEquals(2, response.stops().get(0).lines().size());
        assertEquals(BATCH_1_ID, response.stops().get(0).lines().get(0).stockBatchId());
        assertEquals(10, response.stops().get(0).lines().get(0).quantity());
        assertEquals(5, response.stops().get(0).lines().get(1).quantity());
        verify(accessService).requireWmsAccess(TENANT_ID, WAREHOUSE_ID);
        verify(accessService, never()).requireActiveStaffAssignment(any(), any(), any());
    }

    @Test
    void suggestForAssignedStaffUsesTheSameWarehouseGuard() {
        UUID staffId = UUID.randomUUID();
        stubValidRequest(List.of(batch(BATCH_1_ID, 10, LocalDateTime.now())));
        doNothing().when(accessService).requireActiveStaffAssignment(staffId, TENANT_ID, WAREHOUSE_ID);

        OutboundPickingSuggestionResponse response = suggestionService.suggest(
                TENANT_ID, staffId, WAREHOUSE_ID,
                List.of(new OutboundPickingInputItem(SKU_ID, 5)));

        assertTrue(response.complete());
        verify(accessService).requireActiveStaffAssignment(staffId, TENANT_ID, WAREHOUSE_ID);
    }

    @Test
    void suggestReportsShortageWithoutCreatingReceiptOrReservation() {
        stubValidRequest(List.of(batch(BATCH_1_ID, 3, LocalDateTime.now())));

        OutboundPickingSuggestionResponse response = suggestionService.suggest(
                TENANT_ID, null, WAREHOUSE_ID,
                List.of(new OutboundPickingInputItem(SKU_ID, 5)));

        assertFalse(response.complete());
        assertEquals(3, response.items().get(0).allocatedQuantity());
        assertEquals(2, response.items().get(0).shortageQuantity());
        assertEquals(1, response.stops().size());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void suggestRejectsUnassignedStaffBeforeLoadingLayoutOrStock() {
        UUID staffId = UUID.randomUUID();
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        doThrow(new ForbiddenException(ErrorCode.FORBIDDEN))
                .when(accessService).requireActiveStaffAssignment(staffId, TENANT_ID, WAREHOUSE_ID);

        assertThrows(ForbiddenException.class, () -> suggestionService.suggest(
                TENANT_ID, staffId, WAREHOUSE_ID,
                List.of(new OutboundPickingInputItem(SKU_ID, 5))));
        verify(layoutRepository, never()).findByWarehouseIdAndTenantId(any(), any());
        verify(stockBatchRepository, never())
                .findAllBySkuIdAndWarehouseIdAndIsActiveTrueAndIsDeletedFalse(any(), any());
    }

    @Test
    void suggestRejectsSkuOwnedByAnotherTenant() {
        stubAccessAndLayout();
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(SKU_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> suggestionService.suggest(
                TENANT_ID, null, WAREHOUSE_ID,
                List.of(new OutboundPickingInputItem(SKU_ID, 5))));
        verify(stockBatchRepository, never())
                .findAllBySkuIdAndWarehouseIdAndIsActiveTrueAndIsDeletedFalse(any(), any());
    }

    @Test
    void suggestRejectsDuplicateSkuRequest() {
        assertThrows(fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException.class,
                () -> suggestionService.suggest(
                        TENANT_ID, null, WAREHOUSE_ID,
                        List.of(new OutboundPickingInputItem(SKU_ID, 1),
                                new OutboundPickingInputItem(SKU_ID, 2))));
    }

    private void stubValidRequest(List<StockBatch> batches) {
        stubAccessAndLayout();
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(SKU_ID, TENANT_ID))
                .thenReturn(Optional.of(sku));
        when(stockBatchRepository.findAllBySkuIdAndWarehouseIdAndIsActiveTrueAndIsDeletedFalse(
                SKU_ID, WAREHOUSE_ID)).thenReturn(batches);
    }

    private void stubAccessAndLayout() {
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(warehouse));
        when(layoutRepository.findByWarehouseIdAndTenantId(WAREHOUSE_ID, TENANT_ID))
                .thenReturn(Optional.of(layout));
    }

    private StockBatch batch(UUID id, int quantity, LocalDateTime arrivalDate) {
        return StockBatch.builder()
                .id(id)
                .skuId(SKU_ID)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(quantity)
                .arrivalDate(arrivalDate)
                .createdAt(arrivalDate)
                .build();
    }
}
