package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.dto.PagedStockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.StockSummaryResponse;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockBatchServiceTest {

    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private WarehouseRackRepository rackRepository;
    @Mock private WarehouseBinRepository binRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private RentalContractRepository contractRepository;

    @InjectMocks
    private StockBatchService stockBatchService;

    private UUID tenantId;
    private UUID warehouseId;
    private UUID skuId;
    private UUID batchId;
    private Warehouse warehouse;
    private ProductSku productSku;
    private UnitOfMeasure uom;
    private StockBatch stockBatch;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        warehouse = Warehouse.builder().id(warehouseId).name("Test Warehouse").build();
        uom = UnitOfMeasure.builder().id(UUID.randomUUID()).code("BOX").name("Hộp").build();
        productSku = ProductSku.builder().id(skuId).skuCode("SKU-01").name("Sản phẩm 1").uom(uom).build();

        WarehouseRack rack = WarehouseRack.builder().id(UUID.randomUUID()).zoneName("Zone A").name("Rack 1").build();
        WarehouseBin bin = WarehouseBin.builder().id(UUID.randomUUID()).rack(rack).name("Bin 1").build();

        stockBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(100)
                .arrivalDate(LocalDateTime.now())
                .build();
    }

    @Test
    void testGetStockByWarehouse_Success() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(true);

        Page<StockBatch> page = new PageImpl<>(List.of(stockBatch));
        when(stockBatchRepository.findByWarehouseIdAndTenantId(
                eq(warehouseId), eq(tenantId), any(Pageable.class)))
                .thenReturn(page);
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        Pageable pageable = PageRequest.of(0, 10);
        PagedStockBatchResponse response = stockBatchService.getStockByWarehouse(tenantId, warehouseId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("SKU-01", response.getContent().get(0).getSkuCode());
        assertEquals("BOX", response.getContent().get(0).getUomSymbol());
        assertEquals(100, response.getContent().get(0).getQuantity());
    }

    @Test
    void testGetStockByWarehouse_SubscriptionRequired() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(false);

        Pageable pageable = PageRequest.of(0, 10);
        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> stockBatchService.getStockByWarehouse(tenantId, warehouseId, pageable));

        assertEquals(ErrorCode.SUBSCRIPTION_REQUIRED.getMessage(), ex.getMessage());
    }

    @Test
    void testGetStockByWarehouse_WarehouseNotFound() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        Pageable pageable = PageRequest.of(0, 10);
        assertThrows(ResourceNotFoundException.class,
                () -> stockBatchService.getStockByWarehouse(tenantId, warehouseId, pageable));
    }

    @Test
    void testGetStockByWarehouse_EmptyResult() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(true);

        Page<StockBatch> emptyPage = new PageImpl<>(Collections.emptyList());
        when(stockBatchRepository.findByWarehouseIdAndTenantId(
                eq(warehouseId), eq(tenantId), any(Pageable.class)))
                .thenReturn(emptyPage);

        Pageable pageable = PageRequest.of(0, 10);
        PagedStockBatchResponse response = stockBatchService.getStockByWarehouse(tenantId, warehouseId, pageable);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
    }

    @Test
    void testGetStockByWarehouse_RejectsTenantWithoutActiveContract() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(false);

        Pageable pageable = PageRequest.of(0, 10);
        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> stockBatchService.getStockByWarehouse(tenantId, warehouseId, pageable));

        assertEquals(ErrorCode.FORBIDDEN.getMessage(), exception.getMessage());
        verify(stockBatchRepository, never())
                .findByWarehouseIdAndTenantId(any(), any(), any(Pageable.class));
    }

    // ==================== getStockSummaryByWarehouse ====================

    @Test
    void testGetStockSummaryByWarehouse_ReturnsTenantAggregateOnly() {
        StockBatchRepository.WarehouseStockSummaryProjection projection =
                mock(StockBatchRepository.WarehouseStockSummaryProjection.class);
        when(projection.getProductCount()).thenReturn(3L);
        when(projection.getBatchCount()).thenReturn(7L);
        when(projection.getTotalQuantity()).thenReturn(125L);
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(true);
        when(stockBatchRepository.summarizeByWarehouseIdAndTenantId(warehouseId, tenantId))
                .thenReturn(projection);

        StockBatchService.WarehouseStockSummary summary =
                stockBatchService.getStockSummaryByWarehouse(tenantId, warehouseId);

        assertEquals(warehouseId, summary.warehouseId());
        assertEquals("Test Warehouse", summary.warehouseName());
        assertEquals(3, summary.productCount());
        assertEquals(7, summary.batchCount());
        assertEquals(125, summary.totalQuantity());
    }

    @Test
    void testGetStockSummaryByWarehouse_RejectsTenantWithoutActiveContract() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> stockBatchService.getStockSummaryByWarehouse(tenantId, warehouseId));

        verify(stockBatchRepository, never())
                .summarizeByWarehouseIdAndTenantId(any(), any());
    }

    // ==================== getStockSummaryBySku ====================

    @Test
    void testGetStockSummaryBySku_Success_MultipleLocations() {
        UUID warehouseId2 = UUID.randomUUID();
        Warehouse warehouse2 = Warehouse.builder().id(warehouseId2).name("Kho 2").build();

        WarehouseRack rack = WarehouseRack.builder().id(UUID.randomUUID()).zoneName("Zone A").name("Rack 1").build();

        StockBatch batch1 = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .quantity(60)
                .build();

        StockBatch batch2 = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse2)
                .quantity(40)
                .build();

        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId))
                .thenReturn(Optional.of(productSku));
        when(stockBatchRepository.findBySkuIdInActiveTenantWarehouses(skuId, tenantId))
                .thenReturn(List.of(batch1, batch2));


        StockSummaryResponse summary = stockBatchService.getStockSummaryBySku(tenantId, skuId);

        assertNotNull(summary);
        assertEquals(skuId, summary.getSkuId());
        assertEquals("SKU-01", summary.getSkuCode());
        assertEquals(100, summary.getTotalQuantity());
        assertEquals(2, summary.getLocations().size());
        assertEquals("Zone A", summary.getLocations().get(0).getZoneName());
    }


    @Test
    void testGetStockSummaryBySku_SubscriptionRequired() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(false);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> stockBatchService.getStockSummaryBySku(tenantId, skuId));

        assertEquals(ErrorCode.SUBSCRIPTION_REQUIRED.getMessage(), ex.getMessage());
    }

    @Test
    void testGetStockSummaryBySku_SkuNotFound() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> stockBatchService.getStockSummaryBySku(tenantId, skuId));
    }

    @Test
    void testGetStockSummaryBySku_NoStock() {
        when(subscriptionService.hasActiveSubscription(tenantId)).thenReturn(true);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantId))
                .thenReturn(Optional.of(productSku));
        when(stockBatchRepository.findBySkuIdInActiveTenantWarehouses(skuId, tenantId))
                .thenReturn(Collections.emptyList());

        StockSummaryResponse summary = stockBatchService.getStockSummaryBySku(tenantId, skuId);

        assertNotNull(summary);
        assertEquals(0, summary.getTotalQuantity());
        assertTrue(summary.getLocations().isEmpty());
    }

    // ==================== adjustQuantity ====================

    @Test
    void testAdjustQuantity_Increase_Success() {
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(stockBatch));
        when(stockBatchRepository.save(any(StockBatch.class))).thenReturn(stockBatch);

        stockBatchService.adjustQuantity(batchId, 50);

        verify(stockBatchRepository, times(1)).save(any(StockBatch.class));
        assertEquals(150, stockBatch.getQuantity());
    }

    @Test
    void testAdjustQuantity_Decrease_Success() {
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(stockBatch));
        when(stockBatchRepository.save(any(StockBatch.class))).thenReturn(stockBatch);

        stockBatchService.adjustQuantity(batchId, -30);

        verify(stockBatchRepository, times(1)).save(any(StockBatch.class));
        assertEquals(70, stockBatch.getQuantity());
    }

    @Test
    void testAdjustQuantity_BatchNotFound() {
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> stockBatchService.adjustQuantity(batchId, 10));
    }

    // ==================== findOrCreateBatch ====================

    @Test
    void testFindOrCreateBatch_Found_ExistingBatch() {
        UUID rackId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();

        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, rackId, binId))
                .thenReturn(Optional.of(stockBatch));

        StockBatch result = stockBatchService.findOrCreateBatch(skuId, warehouseId, rackId, binId);

        assertNotNull(result);
        assertEquals(batchId, result.getId());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void testFindOrCreateBatch_NotFound_CreateNew() {
        UUID rackId = UUID.randomUUID();
        UUID binId = UUID.randomUUID();

        WarehouseRack rack = WarehouseRack.builder().id(rackId).name("Rack 1").build();
        WarehouseBin bin = WarehouseBin.builder().id(binId).name("Bin 1").build();

        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, rackId, binId))
                .thenReturn(Optional.empty());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findById(binId)).thenReturn(Optional.of(bin));

        StockBatch newBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(0)
                .arrivalDate(LocalDateTime.now())
                .build();
        when(stockBatchRepository.save(any(StockBatch.class))).thenReturn(newBatch);

        StockBatch result = stockBatchService.findOrCreateBatch(skuId, warehouseId, rackId, binId);

        assertNotNull(result);
        assertEquals(0, result.getQuantity());
        verify(stockBatchRepository, times(1)).save(any(StockBatch.class));
    }

    @Test
    void testFindOrCreateBatch_NullLocationIds_CreateNew() {
        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, null, null))
                .thenReturn(Optional.empty());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        StockBatch newBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .quantity(0)
                .arrivalDate(LocalDateTime.now())
                .build();
        when(stockBatchRepository.save(any(StockBatch.class))).thenReturn(newBatch);

        StockBatch result = stockBatchService.findOrCreateBatch(skuId, warehouseId, null, null);

        assertNotNull(result);
        assertNull(result.getRack());
        assertNull(result.getBin());
    }

    // ==================== getAdminStockByWarehouse ====================

    @Test
    void testGetAdminStockByWarehouse_Success() {
        Page<StockBatch> page = new PageImpl<>(List.of(stockBatch));
        when(stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(eq(warehouseId), any(Pageable.class)))
                .thenReturn(page);
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        Pageable pageable = PageRequest.of(0, 50);
        PagedStockBatchResponse response = stockBatchService.getAdminStockByWarehouse(warehouseId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        verify(subscriptionService, never()).hasActiveSubscription(any());
    }

    @Test
    void testGetAdminStockByWarehouse_EmptyWarehouse() {
        Page<StockBatch> emptyPage = new PageImpl<>(Collections.emptyList());
        when(stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(eq(warehouseId), any(Pageable.class)))
                .thenReturn(emptyPage);

        Pageable pageable = PageRequest.of(0, 50);
        PagedStockBatchResponse response = stockBatchService.getAdminStockByWarehouse(warehouseId, pageable);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
        assertEquals(0, response.getTotalElements());
        assertTrue(response.getTotalPages() >= 0);
    }
}
