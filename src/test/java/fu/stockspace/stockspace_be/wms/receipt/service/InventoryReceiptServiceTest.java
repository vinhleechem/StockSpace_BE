package fu.stockspace.stockspace_be.wms.receipt.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadLine;
import fu.stockspace.stockspace_be.wms.receipt.dto.*;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingSuggestionService;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickLineResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickStopResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingItemResponse;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReceiptServiceTest {

    @Mock private InventoryReceiptRepository receiptRepository;
    @Mock private InventoryReceiptItemRepository receiptItemRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private WarehouseRackRepository rackRepository;
    @Mock private WarehouseBinRepository binRepository;
    @Mock private fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository tenantMemberRepository;
    @Mock private TenantWarehouseAccessService accessService;
    @Mock private fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository assignmentRepository;
    @Mock private fu.stockspace.stockspace_be.notification.service.NotificationService notificationService;
    @Mock private OutboundPickingSuggestionService pickingSuggestionService;
    @Spy private fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator physicalLoadCalculator;

    @InjectMocks
    private InventoryReceiptService receiptService;

    private UUID userId;
    private User tenantUser;
    private UUID warehouseId;
    private Warehouse warehouse;
    private WarehouseLayout layout;
    private UUID skuId;
    private ProductSku productSku;
    private UUID rackId, binId;
    private WarehouseRack rack;
    private WarehouseBin bin;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tenantUser = User.builder().id(userId).email("tenant@test.com").fullName("Tenant User").build();
        warehouseId = UUID.randomUUID();
        warehouse = Warehouse.builder().id(warehouseId).name("Test Warehouse").build();
        layout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(warehouse).build();

        skuId = UUID.randomUUID();
        productSku = ProductSku.builder().id(skuId).skuCode("SKU123").name("Product 1").build();

        rackId = UUID.randomUUID();
        rack = WarehouseRack.builder().id(rackId).layout(layout).name("Rack 1").build();

        binId = UUID.randomUUID();
        bin = WarehouseBin.builder().id(binId).rack(rack).name("Bin 1").build();

        lenient().doNothing().when(accessService)
                .requireActiveStaffAssignment(any(), any(), any());
        lenient().when(receiptRepository.findByIdForUpdate(any()))
                .thenAnswer(invocation -> receiptRepository.findById(invocation.getArgument(0)));
    }

    @Test
    void testCreateReceipt_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(bin));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .senderName("Công ty gửi hàng")
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.save(any(InventoryReceipt.class))).thenReturn(receipt);

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(10)
                .rack(rack)
                .bin(bin)
                .build();
        when(receiptItemRepository.save(any(InventoryReceiptItem.class))).thenReturn(item);

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(10)
                .rackId(rackId)
                .binId(binId)
                .build();

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .senderName("Công ty gửi hàng")
                .items(List.of(itemRequest))
                .build();

        InventoryReceiptResponse response = receiptService.createReceipt(userId, request);

        assertNotNull(response);
        assertEquals(ApprovalStatus.PENDING, response.getStatus());
        assertEquals(DocumentType.INBOUND, response.getType());
        assertEquals("Công ty gửi hàng", response.getSenderName());
        assertEquals(1, response.getItems().size());
        assertEquals("SKU123", response.getItems().get(0).getSkuCode());
        ArgumentCaptor<InventoryReceipt> receiptCaptor = ArgumentCaptor.forClass(InventoryReceipt.class);
        verify(receiptRepository, times(1)).save(receiptCaptor.capture());
        assertEquals("Công ty gửi hàng", receiptCaptor.getValue().getSenderName());
    }

    @Test
    void testCreateReceipt_Inbound_BinCapacityExceeded_ThrowsException() {
        WarehouseBin binWithLimit = WarehouseBin.builder()
                .id(binId)
                .rack(rack)
                .name("Bin Limited")
                .maxWeight(java.math.BigDecimal.valueOf(50))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(binWithLimit));
        productSku.setUnitWeightKg(java.math.BigDecimal.ONE);
        productSku.setUnitVolumeM3(java.math.BigDecimal.ONE);
        StockBatch existingBatch = StockBatch.builder()
                .skuId(skuId)
                .bin(binWithLimit)
                .rack(rack)
                .quantity(40)
                .build();
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, userId))
                .thenReturn(List.of(new PhysicalLoadLine(
                        rackId, binId, skuId, "SKU123", "Product 1",
                        java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, existingBatch.getQuantity())));

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(20)
                .rackId(rackId)
                .binId(binId)
                .build();

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .items(List.of(itemRequest))
                .build();

        assertThrows(BadRequestException.class, () -> receiptService.createReceipt(userId, request));
    }

    @Test
    void testCreateReceipt_Inbound_AggregatesPhysicalWeightAcrossItems() {
        WarehouseRack limitedRack = WarehouseRack.builder()
                .id(rackId)
                .layout(layout)
                .name("Rack Limited")
                .maxWeight(java.math.BigDecimal.valueOf(50))
                .build();
        WarehouseBin unlimitedBin = WarehouseBin.builder()
                .id(binId)
                .rack(limitedRack)
                .name("Bin 1")
                .build();
        productSku.setUnitWeightKg(java.math.BigDecimal.valueOf(2));
        productSku.setUnitVolumeM3(java.math.BigDecimal.valueOf(0.1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(limitedRack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(unlimitedBin));
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, userId))
                .thenReturn(List.of());

        ReceiptItemRequest firstItem = ReceiptItemRequest.builder()
                .skuId(skuId).quantity(15).rackId(rackId).binId(binId).build();
        ReceiptItemRequest secondItem = ReceiptItemRequest.builder()
                .skuId(skuId).quantity(15).rackId(rackId).binId(binId).build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .items(List.of(firstItem, secondItem))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> receiptService.createReceipt(userId, request));

        assertTrue(exception.getMessage().contains("weight capacity exceeded"));
    }

    @Test
    void testCreateReceipt_Inbound_UsesCubicMetersForVolumeCapacity() {
        WarehouseBin volumeLimitedBin = WarehouseBin.builder()
                .id(binId)
                .rack(rack)
                .name("Volume Limited Bin")
                .maxVolume(java.math.BigDecimal.valueOf(0.5))
                .build();
        productSku.setUnitWeightKg(java.math.BigDecimal.ONE);
        productSku.setUnitVolumeM3(java.math.BigDecimal.valueOf(0.1));

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(volumeLimitedBin));
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, userId))
                .thenReturn(List.of());

        ReceiptItemRequest item = ReceiptItemRequest.builder()
                .skuId(skuId).quantity(6).rackId(rackId).binId(binId).build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .items(List.of(item))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> receiptService.createReceipt(userId, request));

        assertTrue(exception.getMessage().contains("volume capacity exceeded"));
    }

    @Test
    void testCreateReceipt_InboundAggregatesActiveBatchesAndSkipsDeletedOrZeroQuantity() {
        WarehouseBin limitedBin = WarehouseBin.builder()
                .id(binId)
                .rack(rack)
                .name("Aggregated Bin")
                .maxWeight(new java.math.BigDecimal("10"))
                .maxVolume(new java.math.BigDecimal("100"))
                .build();
        UUID secondSkuId = UUID.randomUUID();
        ProductSku secondSku = ProductSku.builder()
                .id(secondSkuId)
                .skuCode("SKU456")
                .name("Product 2")
                .unitWeightKg(new java.math.BigDecimal("2"))
                .unitVolumeM3(java.math.BigDecimal.ONE)
                .build();
        productSku.setUnitWeightKg(java.math.BigDecimal.ONE);
        productSku.setUnitVolumeM3(java.math.BigDecimal.ONE);

        StockBatch firstBatch = StockBatch.builder().skuId(skuId).bin(limitedBin).rack(rack).quantity(2).build();
        StockBatch secondBatch = StockBatch.builder().skuId(skuId).bin(limitedBin).rack(rack).quantity(3).build();
        StockBatch thirdBatch = StockBatch.builder().skuId(secondSkuId).bin(limitedBin).rack(rack).quantity(1).build();
        StockBatch deletedBatch = StockBatch.builder().skuId(secondSkuId).bin(limitedBin).rack(rack).quantity(100).build();
        deletedBatch.setDeleted(true);
        StockBatch zeroQuantityBatch = StockBatch.builder().skuId(secondSkuId).bin(limitedBin).rack(rack).quantity(0).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(receiptItemRepository.save(any(InventoryReceiptItem.class))).thenAnswer(i -> i.getArgument(0));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(secondSkuId, userId))
                .thenReturn(Optional.of(secondSku));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(limitedBin));
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, userId))
                .thenReturn(List.of(
                        new PhysicalLoadLine(rackId, binId, skuId, "SKU123", "Product 1",
                                productSku.getUnitWeightKg(), productSku.getUnitVolumeM3(), firstBatch.getQuantity()),
                        new PhysicalLoadLine(rackId, binId, skuId, "SKU123", "Product 1",
                                productSku.getUnitWeightKg(), productSku.getUnitVolumeM3(), secondBatch.getQuantity()),
                        new PhysicalLoadLine(rackId, binId, secondSkuId, "SKU456", "Product 2",
                                secondSku.getUnitWeightKg(), secondSku.getUnitVolumeM3(), thirdBatch.getQuantity()),
                        new PhysicalLoadLine(rackId, binId, secondSkuId, "SKU456", "Product 2",
                                secondSku.getUnitWeightKg(), secondSku.getUnitVolumeM3(), deletedBatch.getQuantity(),
                                deletedBatch.isActive(), deletedBatch.isDeleted()),
                        new PhysicalLoadLine(rackId, binId, secondSkuId, "SKU456", "Product 2",
                                secondSku.getUnitWeightKg(), secondSku.getUnitVolumeM3(), zeroQuantityBatch.getQuantity(),
                                zeroQuantityBatch.isActive(), zeroQuantityBatch.isDeleted())));

        ReceiptItemRequest item = ReceiptItemRequest.builder()
                .skuId(secondSkuId).quantity(2).rackId(rackId).binId(binId).build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId).type(DocumentType.INBOUND).items(List.of(item)).build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> receiptService.createReceipt(userId, request));

        assertTrue(exception.getMessage().contains("weight capacity exceeded"));
    }

    @Test
    void testCreateReceipt_InboundTreatsZeroCapacityAsUnlimited() {
        WarehouseBin unlimitedBin = WarehouseBin.builder()
                .id(binId)
                .rack(rack)
                .name("Unlimited Bin")
                .maxWeight(java.math.BigDecimal.ZERO)
                .maxVolume(java.math.BigDecimal.ZERO)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(receiptItemRepository.save(any(InventoryReceiptItem.class))).thenAnswer(i -> i.getArgument(0));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(unlimitedBin));

        ReceiptItemRequest item = ReceiptItemRequest.builder()
                .skuId(skuId).quantity(100).rackId(rackId).binId(binId).build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId).type(DocumentType.INBOUND).items(List.of(item)).build();

        assertDoesNotThrow(() -> receiptService.createReceipt(userId, request));
        verify(stockBatchRepository, never())
                .findActivePhysicalLoadsByWarehouseIdAndTenantId(warehouseId, userId);
    }

    @Test
    void testCreateReceipt_Outbound_GeneratesFifoAllocationsAndPickList() {
        UUID firstBatchId = UUID.randomUUID();
        UUID secondBatchId = UUID.randomUUID();
        StockBatch firstBatch = StockBatch.builder()
                .id(firstBatchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(40)
                .arrivalDate(java.time.LocalDateTime.of(2026, 1, 8, 0, 0))
                .build();
        StockBatch secondBatch = StockBatch.builder()
                .id(secondBatchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(60)
                .arrivalDate(java.time.LocalDateTime.of(2026, 5, 8, 0, 0))
                .build();
        OutboundPickingSuggestionResponse pickList = new OutboundPickingSuggestionResponse(
                warehouseId,
                layout.getId(),
                "FIFO_SERPENTINE_XY_V1",
                true,
                List.of(new OutboundPickingItemResponse(skuId, 50, 50, 0)),
                List.of(new OutboundPickStopResponse(
                        1,
                        rackId,
                        "RACK-1",
                        binId,
                        "BIN-1",
                        1,
                        List.of(
                                new OutboundPickLineResponse(firstBatchId, skuId, "SKU123", "Product 1",
                                        firstBatch.getArrivalDate(), 40),
                                new OutboundPickLineResponse(secondBatchId, skuId, "SKU123", "Product 1",
                                        secondBatch.getArrivalDate(), 10)))),
                List.of());

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(pickingSuggestionService.suggest(any(), any(), any(), any())).thenReturn(pickList);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(stockBatchRepository.findAllById(any())).thenReturn(List.of(firstBatch, secondBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any(InventoryReceiptItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(50)
                .note("Outbound note")
                .build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.OUTBOUND)
                .signatureData("signature")
                .receiverName("Công ty nhận hàng")
                .items(List.of(itemRequest))
                .build();

        InventoryReceiptResponse response = receiptService.createReceipt(userId, request);

        assertNotNull(response);
        assertEquals(DocumentType.OUTBOUND, response.getType());
        assertEquals(ApprovalStatus.PENDING, response.getStatus());
        assertEquals("signature", response.getSignatureData());
        assertEquals("Công ty nhận hàng", response.getReceiverName());
        assertEquals(pickList, response.getPickList());
        assertEquals(2, response.getItems().size());
        assertEquals(firstBatchId, response.getItems().get(0).getStockBatchId());
        assertEquals(secondBatchId, response.getItems().get(1).getStockBatchId());
        assertEquals(1, response.getItems().get(0).getPickSequence());
        assertEquals(1, response.getItems().get(1).getPickSequence());
        verify(rackRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(binRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(receiptRepository).save(any(InventoryReceipt.class));
        verify(receiptItemRepository, times(2)).save(any(InventoryReceiptItem.class));
    }

    @Test
    void testCreateReceipt_Outbound_GroupsMultipleSkusInTheSameBin() {
        UUID secondSkuId = UUID.randomUUID();
        ProductSku secondSku = ProductSku.builder()
                .id(secondSkuId)
                .skuCode("SKU456")
                .name("Product 2")
                .build();
        UUID firstBatchId = UUID.randomUUID();
        UUID secondBatchId = UUID.randomUUID();
        StockBatch firstBatch = StockBatch.builder()
                .id(firstBatchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(40)
                .build();
        StockBatch secondBatch = StockBatch.builder()
                .id(secondBatchId)
                .skuId(secondSkuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(20)
                .build();
        OutboundPickingSuggestionResponse pickList = new OutboundPickingSuggestionResponse(
                warehouseId,
                layout.getId(),
                "FIFO_SERPENTINE_XY_V1",
                true,
                List.of(
                        new OutboundPickingItemResponse(skuId, 10, 10, 0),
                        new OutboundPickingItemResponse(secondSkuId, 5, 5, 0)),
                List.of(new OutboundPickStopResponse(
                        1,
                        rackId,
                        "RACK-1",
                        binId,
                        "BIN-1",
                        1,
                        List.of(
                                new OutboundPickLineResponse(firstBatchId, skuId, "SKU123", "Product 1",
                                        firstBatch.getArrivalDate(), 10),
                                new OutboundPickLineResponse(secondBatchId, secondSkuId, "SKU456", "Product 2",
                                        secondBatch.getArrivalDate(), 5)))),
                List.of());

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(pickingSuggestionService.suggest(any(), any(), any(), any())).thenReturn(pickList);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(secondSkuId, userId))
                .thenReturn(Optional.of(secondSku));
        when(stockBatchRepository.findAllById(any())).thenReturn(List.of(firstBatch, secondBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any(InventoryReceiptItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.OUTBOUND)
                .items(List.of(
                        ReceiptItemRequest.builder().skuId(skuId).quantity(10).build(),
                        ReceiptItemRequest.builder().skuId(secondSkuId).quantity(5).build()))
                .build();

        InventoryReceiptResponse response = receiptService.createReceipt(userId, request);

        assertEquals(2, response.getItems().size());
        assertEquals(1, response.getItems().get(0).getPickSequence());
        assertEquals(1, response.getItems().get(1).getPickSequence());
        assertEquals(binId, response.getItems().get(0).getBinId());
        assertEquals(binId, response.getItems().get(1).getBinId());
        assertEquals(secondSkuId, response.getItems().get(1).getSkuId());
    }

    @Test
    void testCreateReceipt_Outbound_RejectsManualLocation() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(10)
                .rackId(rackId)
                .binId(binId)
                .build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.OUTBOUND)
                .items(List.of(itemRequest))
                .build();

        assertThrows(BadRequestException.class, () -> receiptService.createReceipt(userId, request));
        verify(pickingSuggestionService, never()).suggest(any(), any(), any(), any());
        verify(receiptRepository, never()).save(any(InventoryReceipt.class));
    }

    @Test
    void testCreateReceipt_Outbound_InsufficientStockDoesNotCreateReceipt() {
        OutboundPickingSuggestionResponse incompletePickList = new OutboundPickingSuggestionResponse(
                warehouseId,
                layout.getId(),
                "FIFO_SERPENTINE_XY_V1",
                false,
                List.of(new OutboundPickingItemResponse(skuId, 100, 40, 60)),
                List.of(),
                List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(pickingSuggestionService.suggest(any(), any(), any(), any())).thenReturn(incompletePickList);

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(100)
                .build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.OUTBOUND)
                .items(List.of(itemRequest))
                .build();

        assertThrows(BadRequestException.class, () -> receiptService.createReceipt(userId, request));
        verify(receiptRepository, never()).save(any(InventoryReceipt.class));
        verify(receiptItemRepository, never()).save(any(InventoryReceiptItem.class));
        verify(stockBatchRepository, never()).findAllById(any());
    }

    @Test
    void testCreateReceipt_SubscriptionRequired() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        doThrow(new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED))
                .when(accessService).requireActiveSubscription(userId);

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .build();

        assertThrows(ForbiddenException.class, () -> receiptService.createReceipt(userId, request));
    }

    @Test
    void testCreateReceipt_InvalidCoordinates_RackNotInWarehouse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));

        Warehouse alternateWarehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        WarehouseLayout alternateLayout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(alternateWarehouse).build();
        WarehouseRack invalidRack = WarehouseRack.builder().id(rackId).layout(alternateLayout).build();
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(invalidRack));

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(10)
                .rackId(rackId)
                .binId(binId)
                .build();

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .items(List.of(itemRequest))
                .build();

        assertThrows(BadRequestException.class, () -> receiptService.createReceipt(userId, request));
    }

    @Test
    void testCreateReceipt_RejectsSkuOwnedByAnotherTenant() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.empty());

        ReceiptItemRequest itemRequest = ReceiptItemRequest.builder()
                .skuId(skuId)
                .quantity(10)
                .rackId(rackId)
                .binId(binId)
                .build();
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .items(List.of(itemRequest))
                .build();

        assertThrows(fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException.class,
                () -> receiptService.createReceipt(userId, request));
    }

    @Test
    void testCreateReceipt_StaffWithoutWarehouseAssignment_ThrowsForbiddenException() {
        UUID staffId = UUID.randomUUID();
        fu.stockspace.stockspace_be.auth.entity.Role staffRole =
                fu.stockspace.stockspace_be.auth.entity.Role.builder()
                        .name(fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name())
                        .build();
        User staff = User.builder().id(staffId).roles(java.util.Set.of(staffRole)).build();
        fu.stockspace.stockspace_be.staff.entity.TenantMember membership =
                fu.stockspace.stockspace_be.staff.entity.TenantMember.builder()
                        .tenant(tenantUser)
                        .user(staff)
                        .isActive(true)
                        .isDeleted(false)
                        .build();

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId))
                .thenReturn(Optional.of(membership));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        doThrow(new ForbiddenException(ErrorCode.FORBIDDEN))
                .when(accessService).requireActiveStaffAssignment(staffId, userId, warehouseId);

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .items(List.of())
                .build();

        assertThrows(ForbiddenException.class, () -> receiptService.createReceipt(staffId, request));
        verify(receiptRepository, never()).save(any(InventoryReceipt.class));
    }

    @Test
    void testApproveReceipt_ByStaff_ThrowsForbiddenException() {
        UUID staffId = UUID.randomUUID();
        fu.stockspace.stockspace_be.auth.entity.Role staffRole = fu.stockspace.stockspace_be.auth.entity.Role.builder()
                .name(fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name()).build();
        User staffUser = User.builder().id(staffId).roles(java.util.Set.of(staffRole)).build();
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));

        UUID receiptId = UUID.randomUUID();
        assertThrows(ForbiddenException.class, () -> receiptService.approveReceipt(staffId, receiptId));
    }

    @Test
    void testApproveReceipt_Success_Inbound_NewBatch() {
        UUID approverId = userId;
        User approver = tenantUser;
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(50)
                .rack(rack)
                .bin(bin)
                .build();
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(item));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any(InventoryReceiptItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.save(any(StockBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.approveReceipt(approverId, receipt.getId());

        assertNotNull(response);
        assertEquals(ApprovalStatus.APPROVED, response.getStatus());
        assertNotNull(item.getStockBatch());
        assertEquals(50, item.getStockBatch().getQuantity());
        assertEquals(rack, item.getStockBatch().getRack());
        assertEquals(bin, item.getStockBatch().getBin());
        assertNotNull(item.getStockBatch().getArrivalDate());
        verify(stockBatchRepository, times(1)).save(any(StockBatch.class));
        verify(receiptItemRepository, times(1)).save(item);
        verify(transactionRepository, times(1)).save(any(InventoryTransaction.class));
    }

    @Test
    void testApproveReceipt_Success_Inbound_CreatesSeparateBatchPerReceipt() {
        UUID approverId = userId;
        User approver = tenantUser;
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        InventoryReceipt firstReceipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceipt secondReceipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.findById(firstReceipt.getId())).thenReturn(Optional.of(firstReceipt));
        when(receiptRepository.findById(secondReceipt.getId())).thenReturn(Optional.of(secondReceipt));

        InventoryReceiptItem firstItem = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(firstReceipt)
                .sku(productSku)
                .quantity(40)
                .rack(rack)
                .bin(bin)
                .build();
        InventoryReceiptItem secondItem = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(secondReceipt)
                .sku(productSku)
                .quantity(60)
                .rack(rack)
                .bin(bin)
                .build();
        when(receiptItemRepository.findByReceiptId(firstReceipt.getId())).thenReturn(List.of(firstItem));
        when(receiptItemRepository.findByReceiptId(secondReceipt.getId())).thenReturn(List.of(secondItem));

        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any(InventoryReceiptItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.save(any(StockBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        receiptService.approveReceipt(approverId, firstReceipt.getId());
        receiptService.approveReceipt(approverId, secondReceipt.getId());

        assertNotNull(firstItem.getStockBatch());
        assertNotNull(secondItem.getStockBatch());
        assertNotSame(firstItem.getStockBatch(), secondItem.getStockBatch());
        assertEquals(40, firstItem.getStockBatch().getQuantity());
        assertEquals(60, secondItem.getStockBatch().getQuantity());
        assertEquals(rack, firstItem.getStockBatch().getRack());
        assertEquals(bin, secondItem.getStockBatch().getBin());
        verify(stockBatchRepository, times(2)).save(any(StockBatch.class));
        verify(receiptItemRepository).save(firstItem);
        verify(receiptItemRepository).save(secondItem);
        verify(transactionRepository, times(2)).save(any(InventoryTransaction.class));
    }

    @Test
    void testApproveReceipt_Success_Outbound() {
        UUID approverId = userId;
        User approver = tenantUser;
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(30)
                .rack(rack)
                .bin(bin)
                .build();
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(item));

        StockBatch existingBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(100)
                .build();
        item.setStockBatch(existingBatch);
        item.setPickSequence(1);
        when(stockBatchRepository.findByIdForUpdate(existingBatch.getId()))
                .thenReturn(Optional.of(existingBatch));

        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.approveReceipt(approverId, receipt.getId());

        assertNotNull(response);
        assertEquals(ApprovalStatus.APPROVED, response.getStatus());
        assertEquals(70, existingBatch.getQuantity());
        verify(stockBatchRepository, times(1)).save(existingBatch);
        verify(transactionRepository, times(1)).save(any(InventoryTransaction.class));
        assertThrows(BadRequestException.class,
                () -> receiptService.approveReceipt(approverId, receipt.getId()));
    }

    @Test
    void testApproveReceipt_Outbound_InsufficientQuantity() {
        UUID approverId = userId;
        User approver = tenantUser;
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(150)
                .rack(rack)
                .bin(bin)
                .build();
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(item));

        StockBatch existingBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(100)
                .build();
        item.setStockBatch(existingBatch);
        item.setPickSequence(1);
        when(stockBatchRepository.findByIdForUpdate(existingBatch.getId()))
                .thenReturn(Optional.of(existingBatch));

        assertThrows(ResourceConflictException.class,
                () -> receiptService.approveReceipt(approverId, receipt.getId()));
    }

    @Test
    void testApproveReceipt_Outbound_UsesStableBatchLockOrder() {
        UUID firstBatchId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondBatchId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID secondSkuId = UUID.randomUUID();
        ProductSku secondSku = ProductSku.builder()
                .id(secondSkuId)
                .skuCode("SKU456")
                .name("Product 2")
                .build();
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        StockBatch secondBatch = StockBatch.builder()
                .id(secondBatchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(40)
                .build();
        StockBatch firstBatch = StockBatch.builder()
                .id(firstBatchId)
                .skuId(secondSkuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(30)
                .build();
        InventoryReceiptItem secondItem = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(20)
                .rack(rack)
                .bin(bin)
                .stockBatch(secondBatch)
                .pickSequence(2)
                .build();
        InventoryReceiptItem firstItem = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(secondSku)
                .quantity(10)
                .rack(rack)
                .bin(bin)
                .stockBatch(firstBatch)
                .pickSequence(1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceiptId(receipt.getId()))
                .thenReturn(List.of(secondItem, firstItem));
        when(stockBatchRepository.findByIdForUpdate(firstBatchId)).thenReturn(Optional.of(firstBatch));
        when(stockBatchRepository.findByIdForUpdate(secondBatchId)).thenReturn(Optional.of(secondBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.approveReceipt(userId, receipt.getId());

        assertEquals(ApprovalStatus.APPROVED, response.getStatus());
        assertEquals(20, firstBatch.getQuantity());
        assertEquals(20, secondBatch.getQuantity());
        InOrder inOrder = inOrder(stockBatchRepository);
        inOrder.verify(stockBatchRepository).findByIdForUpdate(firstBatchId);
        inOrder.verify(stockBatchRepository).findByIdForUpdate(secondBatchId);
        verify(transactionRepository, times(2)).save(any(InventoryTransaction.class));
    }

    @Test
    void testApproveReceipt_Outbound_CompetingReceiptCannotOversellBatch() {
        UUID batchId = UUID.randomUUID();
        StockBatch sharedBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(100)
                .build();
        InventoryReceipt firstReceipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceipt secondReceipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptItem firstItem = outboundApprovalItem(firstReceipt, sharedBatch, 80, 1);
        InventoryReceiptItem secondItem = outboundApprovalItem(secondReceipt, sharedBatch, 80, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findById(firstReceipt.getId())).thenReturn(Optional.of(firstReceipt));
        when(receiptRepository.findById(secondReceipt.getId())).thenReturn(Optional.of(secondReceipt));
        when(receiptItemRepository.findByReceiptId(firstReceipt.getId())).thenReturn(List.of(firstItem));
        when(receiptItemRepository.findByReceiptId(secondReceipt.getId())).thenReturn(List.of(secondItem));
        when(stockBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(sharedBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        receiptService.approveReceipt(userId, firstReceipt.getId());

        assertThrows(ResourceConflictException.class,
                () -> receiptService.approveReceipt(userId, secondReceipt.getId()));
        assertEquals(20, sharedBatch.getQuantity());
        assertEquals(ApprovalStatus.PENDING, secondReceipt.getStatus());
        verify(stockBatchRepository, times(1)).save(sharedBatch);
        verify(transactionRepository, times(1)).save(any(InventoryTransaction.class));
    }

    @Test
    void testApproveReceipt_Outbound_StaleLocationDoesNotMutateStock() {
        WarehouseRack staleRack = WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(layout)
                .name("Stale Rack")
                .build();
        UUID batchId = UUID.randomUUID();
        StockBatch lockedBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(100)
                .build();
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(10)
                .rack(staleRack)
                .bin(bin)
                .stockBatch(lockedBatch)
                .pickSequence(1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(item));
        when(stockBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(lockedBatch));

        ResourceConflictException exception = assertThrows(ResourceConflictException.class,
                () -> receiptService.approveReceipt(userId, receipt.getId()));
        assertEquals(ErrorCode.OUTBOUND_PICK_LIST_STALE, exception.getErrorCode());
        assertEquals(100, lockedBatch.getQuantity());
        assertEquals(ApprovalStatus.PENDING, receipt.getStatus());
        verify(stockBatchRepository, never()).save(any(StockBatch.class));
        verify(transactionRepository, never()).save(any(InventoryTransaction.class));
        verify(receiptRepository, never()).save(any(InventoryReceipt.class));
    }

    private InventoryReceiptItem outboundApprovalItem(
            InventoryReceipt receipt, StockBatch batch, int quantity, int pickSequence) {
        return InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(quantity)
                .rack(rack)
                .bin(bin)
                .stockBatch(batch)
                .pickSequence(pickSequence)
                .build();
    }

    @Test
    void testReplanOutboundReceipt_TenantReplacesItemsWithCurrentFifoPlan() {
        UUID oldBatchId = UUID.randomUUID();
        UUID newBatchId = UUID.randomUUID();
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptItem firstOldItem = outboundApprovalItem(
                receipt,
                StockBatch.builder().id(oldBatchId).skuId(skuId).warehouse(warehouse)
                        .rack(rack).bin(bin).quantity(30).build(),
                30,
                1);
        InventoryReceiptItem secondOldItem = outboundApprovalItem(
                receipt,
                StockBatch.builder().id(UUID.randomUUID()).skuId(skuId).warehouse(warehouse)
                        .rack(rack).bin(bin).quantity(20).build(),
                20,
                2);
        StockBatch newBatch = StockBatch.builder()
                .id(newBatchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(50)
                .arrivalDate(java.time.LocalDateTime.of(2026, 8, 31, 0, 0))
                .build();
        OutboundPickingSuggestionResponse replacementPickList = completePickList(
                new OutboundPickStopResponse(
                        1, rackId, "RACK-1", binId, "BIN-1", 1,
                        List.of(new OutboundPickLineResponse(
                                newBatchId, skuId, "SKU123", "Product 1",
                                newBatch.getArrivalDate(), 50))));

        List<InventoryReceiptItem> currentItems = List.of(firstOldItem, secondOldItem);
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(currentItems);
        when(pickingSuggestionService.suggest(eq(userId), isNull(), eq(warehouseId), any()))
                .thenReturn(replacementPickList);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(stockBatchRepository.findAllById(any())).thenReturn(List.of(newBatch));
        when(receiptItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.replanOutboundReceipt(userId, receipt.getId());

        assertEquals(ApprovalStatus.PENDING, response.getStatus());
        assertEquals(replacementPickList, response.getPickList());
        assertEquals(1, response.getItems().size());
        assertEquals(newBatchId, response.getItems().get(0).getStockBatchId());
        assertEquals(50, response.getItems().get(0).getQuantity());
        verify(receiptItemRepository).deleteAll(currentItems);
        verify(receiptItemRepository).saveAll(any());
    }

    @Test
    void testReplanOutboundReceipt_StaffUsesAssignedWarehouseContext() {
        UUID staffId = UUID.randomUUID();
        fu.stockspace.stockspace_be.auth.entity.Role staffRole =
                fu.stockspace.stockspace_be.auth.entity.Role.builder()
                        .name(fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name())
                        .build();
        User staff = User.builder().id(staffId).roles(java.util.Set.of(staffRole)).build();
        fu.stockspace.stockspace_be.staff.entity.TenantMember membership =
                fu.stockspace.stockspace_be.staff.entity.TenantMember.builder()
                        .tenant(tenantUser)
                        .user(staff)
                        .isActive(true)
                        .isDeleted(false)
                        .build();
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptItem oldItem = outboundApprovalItem(
                receipt,
                StockBatch.builder().id(UUID.randomUUID()).skuId(skuId).warehouse(warehouse)
                        .rack(rack).bin(bin).quantity(10).build(),
                10,
                1);
        StockBatch replacementBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(10)
                .build();
        OutboundPickingSuggestionResponse replacementPickList = completePickList(
                new OutboundPickStopResponse(
                        1, rackId, "RACK-1", binId, "BIN-1", 1,
                        List.of(new OutboundPickLineResponse(
                                replacementBatch.getId(), skuId, "SKU123", "Product 1",
                                replacementBatch.getArrivalDate(), 10))));

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(oldItem));
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId))
                .thenReturn(Optional.of(membership));
        when(pickingSuggestionService.suggest(eq(tenantUser.getId()), eq(staffId), eq(warehouseId), any()))
                .thenReturn(replacementPickList);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, tenantUser.getId()))
                .thenReturn(Optional.of(productSku));
        when(stockBatchRepository.findAllById(any())).thenReturn(List.of(replacementBatch));
        when(receiptItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> receiptService.replanOutboundReceipt(staffId, receipt.getId()));
        verify(pickingSuggestionService).suggest(eq(tenantUser.getId()), eq(staffId), eq(warehouseId), any());
        verify(accessService).requireActiveStaffAssignment(staffId, tenantUser.getId(), warehouseId);
    }

    @Test
    void testReplanOutboundReceipt_RejectsNonPendingReceiptWithoutReplacingItems() {
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.APPROVED)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThrows(BadRequestException.class,
                () -> receiptService.replanOutboundReceipt(userId, receipt.getId()));
        verifyNoInteractions(pickingSuggestionService);
        verify(receiptItemRepository, never()).deleteAll(any());
        verify(receiptItemRepository, never()).saveAll(any());
    }

    @Test
    void testReplanOutboundReceipt_ShortageKeepsOriginalPickList() {
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptItem oldItem = outboundApprovalItem(
                receipt,
                StockBatch.builder().id(UUID.randomUUID()).skuId(skuId).warehouse(warehouse)
                        .rack(rack).bin(bin).quantity(20).build(),
                20,
                1);
        OutboundPickingSuggestionResponse shortagePickList = new OutboundPickingSuggestionResponse(
                warehouseId, layout.getId(), "FIFO_SERPENTINE_XY_V1", false,
                List.of(new OutboundPickingItemResponse(skuId, 20, 0, 20)),
                List.of(),
                List.of("Insufficient stock"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(oldItem));
        when(pickingSuggestionService.suggest(eq(userId), isNull(), eq(warehouseId), any()))
                .thenReturn(shortagePickList);

        ResourceConflictException exception = assertThrows(ResourceConflictException.class,
                () -> receiptService.replanOutboundReceipt(userId, receipt.getId()));

        assertEquals(ErrorCode.OUTBOUND_PICK_LIST_STALE, exception.getErrorCode());
        assertEquals(20, oldItem.getQuantity());
        verify(receiptItemRepository, never()).deleteAll(any());
        verify(receiptItemRepository, never()).saveAll(any());
    }

    @Test
    void testReplanOutboundReceipt_PreservesPlannerStopAndLineOrder() {
        UUID firstBatchId = UUID.randomUUID();
        UUID secondBatchId = UUID.randomUUID();
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.OUTBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        InventoryReceiptItem oldItem = outboundApprovalItem(
                receipt,
                StockBatch.builder().id(UUID.randomUUID()).skuId(skuId).warehouse(warehouse)
                        .rack(rack).bin(bin).quantity(30).build(),
                30,
                1);
        StockBatch firstBatch = StockBatch.builder().id(firstBatchId).skuId(skuId)
                .warehouse(warehouse).rack(rack).bin(bin).quantity(10).build();
        StockBatch secondBatch = StockBatch.builder().id(secondBatchId).skuId(skuId)
                .warehouse(warehouse).rack(rack).bin(bin).quantity(20).build();
        OutboundPickingSuggestionResponse replacementPickList = new OutboundPickingSuggestionResponse(
                warehouseId, layout.getId(), "FIFO_SERPENTINE_XY_V1", true,
                List.of(new OutboundPickingItemResponse(skuId, 30, 30, 0)),
                List.of(
                        new OutboundPickStopResponse(1, rackId, "RACK-1", binId, "BIN-1", 1,
                                List.of(new OutboundPickLineResponse(
                                        firstBatchId, skuId, "SKU123", "Product 1",
                                        firstBatch.getArrivalDate(), 10))),
                        new OutboundPickStopResponse(2, rackId, "RACK-1", binId, "BIN-1", 1,
                                List.of(new OutboundPickLineResponse(
                                        secondBatchId, skuId, "SKU123", "Product 1",
                                        secondBatch.getArrivalDate(), 20)))),
                List.of());
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(receiptRepository.findByIdForUpdate(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(oldItem));
        when(pickingSuggestionService.suggest(eq(userId), isNull(), eq(warehouseId), any()))
                .thenReturn(replacementPickList);
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(stockBatchRepository.findAllById(any())).thenReturn(List.of(firstBatch, secondBatch));
        when(receiptItemRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<List<InventoryReceiptItem>> replacementCaptor =
                ArgumentCaptor.forClass(List.class);
        receiptService.replanOutboundReceipt(userId, receipt.getId());

        verify(receiptItemRepository).saveAll(replacementCaptor.capture());
        List<InventoryReceiptItem> replacementItems = replacementCaptor.getValue();
        assertEquals(2, replacementItems.size());
        assertEquals(firstBatchId, replacementItems.get(0).getStockBatch().getId());
        assertEquals(1, replacementItems.get(0).getPickSequence());
        assertEquals(secondBatchId, replacementItems.get(1).getStockBatch().getId());
        assertEquals(2, replacementItems.get(1).getPickSequence());
    }

    private OutboundPickingSuggestionResponse completePickList(OutboundPickStopResponse... stops) {
        return new OutboundPickingSuggestionResponse(
                warehouseId,
                layout.getId(),
                "FIFO_SERPENTINE_XY_V1",
                true,
                List.of(),
                List.of(stops),
                List.of());
    }

    @Test
    void testCreateAdjustmentReceipt_Outbound_ReconcilesStockAndHistory() {
        UUID auditId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        StockBatch batch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(warehouse)
                .rack(rack)
                .bin(bin)
                .quantity(100)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batch.getId())).thenReturn(Optional.of(batch));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(invocation -> {
            InventoryReceipt receipt = invocation.getArgument(0);
            receipt.setId(receiptId);
            return receipt;
        });
        when(receiptItemRepository.save(any(InventoryReceiptItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceipt receipt = receiptService.createAdjustmentReceipt(
                userId, auditId, warehouseId, DocumentType.OUTBOUND, batch.getId(), 15);

        assertEquals(receiptId, receipt.getId());
        assertEquals(auditId, receipt.getReferenceId());
        assertEquals(DocumentType.OUTBOUND, receipt.getType());
        assertEquals(85, batch.getQuantity());

        ArgumentCaptor<InventoryReceiptItem> itemCaptor = ArgumentCaptor.forClass(InventoryReceiptItem.class);
        verify(receiptItemRepository).save(itemCaptor.capture());
        assertEquals(receipt, itemCaptor.getValue().getReceipt());
        assertEquals(15, itemCaptor.getValue().getQuantity());
        assertEquals(batch.getBin(), itemCaptor.getValue().getBin());
        assertEquals(batch, itemCaptor.getValue().getStockBatch());

        ArgumentCaptor<InventoryTransaction> transactionCaptor =
                ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(transactionRepository).save(transactionCaptor.capture());
        assertEquals(receipt, transactionCaptor.getValue().getReceipt());
        assertEquals(batch, transactionCaptor.getValue().getBatch());
        assertEquals(-15, transactionCaptor.getValue().getQuantityChanged());
        verify(stockBatchRepository).save(batch);
    }

    @Test
    void testRejectReceipt_Success() {
        UUID approverId = userId;
        User approver = tenantUser;
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of());

        InventoryReceiptResponse response = receiptService.rejectReceipt(approverId, receipt.getId(), "Hàng bị lỗi");

        assertNotNull(response);
        assertEquals(ApprovalStatus.REJECTED, response.getStatus());
        assertEquals("Hàng bị lỗi", response.getRejectReason());
        verify(receiptRepository, times(1)).save(any(InventoryReceipt.class));
        verify(notificationService, times(1)).push(eq(tenantUser.getId()), anyString(), anyString(), eq("RECEIPT"));
    }

    @Test
    void testRejectReceipt_ByStaff_ThrowsForbiddenException() {
        UUID staffId = UUID.randomUUID();
        fu.stockspace.stockspace_be.auth.entity.Role staffRole = fu.stockspace.stockspace_be.auth.entity.Role.builder()
                .name(fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name()).build();
        User staffUser = User.builder().id(staffId).roles(java.util.Set.of(staffRole)).build();
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staffUser));

        UUID receiptId = UUID.randomUUID();
        assertThrows(ForbiddenException.class, () -> receiptService.rejectReceipt(staffId, receiptId, "Reason"));
    }

    @Test
    void testRejectReceipt_AlreadyProcessed_ThrowsBadRequestException() {
        UUID approverId = userId;
        User approver = tenantUser;
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .status(ApprovalStatus.APPROVED)
                .build();
        when(receiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        assertThrows(BadRequestException.class, () -> receiptService.rejectReceipt(approverId, receipt.getId(), "Reason"));
    }

    @Test
    void exportReceiptsToCsv_IncludesSenderAndReceiverColumns() {
        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
                .senderName("Công ty gửi hàng")
                .status(ApprovalStatus.PENDING)
                .build();
        when(receiptRepository.findByWarehouseIdAndTypeAndIsDeletedFalse(
                warehouseId, DocumentType.INBOUND, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(receipt)));
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of());

        String csv = new String(
                receiptService.exportReceiptsToCsv(warehouseId, DocumentType.INBOUND),
                StandardCharsets.UTF_8);

        assertTrue(csv.contains("Tên Nơi Gửi,Tên Nơi Nhận"));
        assertTrue(csv.contains("\"Công ty gửi hàng\",\"\""));
    }
}
