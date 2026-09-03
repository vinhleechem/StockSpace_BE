package fu.stockspace.stockspace_be.wms.transfer;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.wms.capacity.PhysicalLoadCalculator;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.dto.ReceiveStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDestinationAllocationRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferDestinationAllocation;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferItem;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferSourceAllocation;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferReceiveServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private WarehouseLayoutRepository layoutRepository;
    @Mock
    private WarehouseRackRepository rackRepository;
    @Mock
    private WarehouseBinRepository binRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private InventoryReceiptRepository receiptRepository;
    @Mock
    private InventoryReceiptItemRepository receiptItemRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;
    @Mock
    private NotificationService notificationService;
    @Spy
    private PhysicalLoadCalculator physicalLoadCalculator = new PhysicalLoadCalculator();

    @InjectMocks
    private StockTransferService transferService;

    private UUID tenantId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private UUID layoutId;
    private UUID rackId;
    private UUID binId;
    private UUID itemId;
    private UUID skuId;
    private User tenant;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private WarehouseLayout tenantLayout;
    private WarehouseRack destinationRack;
    private WarehouseBin destinationBin;
    private ProductSku sku;
    private StockTransferItem item;
    private StockTransfer transfer;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();
        layoutId = UUID.randomUUID();
        rackId = UUID.randomUUID();
        binId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        skuId = UUID.randomUUID();

        Role tenantRole = Role.builder().name(RoleType.ROLE_TENANT.name()).build();
        tenant = User.builder().id(tenantId).fullName("Tenant").roles(Set.of(tenantRole)).build();
        sourceWarehouse = Warehouse.builder().id(sourceWarehouseId).name("Source").build();
        destinationWarehouse = Warehouse.builder().id(destinationWarehouseId).name("Destination").build();
        tenantLayout = WarehouseLayout.builder()
                .id(layoutId)
                .warehouse(destinationWarehouse)
                .tenant(tenant)
                .isDefault(false)
                .build();
        destinationRack = WarehouseRack.builder()
                .id(rackId)
                .layout(tenantLayout)
                .name("Destination Rack")
                .maxWeight(new BigDecimal("100"))
                .maxVolume(new BigDecimal("100"))
                .build();
        destinationBin = WarehouseBin.builder()
                .id(binId)
                .rack(destinationRack)
                .name("Destination Bin")
                .maxWeight(new BigDecimal("100"))
                .maxVolume(new BigDecimal("100"))
                .build();
        sku = ProductSku.builder()
                .id(skuId)
                .tenant(tenant)
                .skuCode("SKU-001")
                .name("Product 1")
                .unitWeightKg(new BigDecimal("2"))
                .unitVolumeM3(new BigDecimal("0.5"))
                .build();

        StockBatch sourceBatch = StockBatch.builder()
                .id(UUID.randomUUID())
                .skuId(skuId)
                .warehouse(sourceWarehouse)
                .quantity(5)
                .build();
        StockTransferSourceAllocation sourceAllocation = StockTransferSourceAllocation.builder()
                .id(UUID.randomUUID())
                .sourceStockBatch(sourceBatch)
                .sourceRack(WarehouseRack.builder().id(UUID.randomUUID()).name("Source Rack").build())
                .sourceBin(WarehouseBin.builder().id(UUID.randomUUID()).name("Source Bin").build())
                .quantity(5)
                .build();
        item = StockTransferItem.builder()
                .id(itemId)
                .sku(sku)
                .requestedQuantity(5)
                .sourceAllocations(new ArrayList<>(List.of(sourceAllocation)))
                .destinationAllocations(new ArrayList<>())
                .build();
        sourceAllocation.setItem(item);
        transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .createdBy(tenant)
                .status(StockTransferStatus.IN_TRANSIT)
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setTransfer(transfer);
    }

    @Test
    void receiveTransfer_addsDestinationStockAndAuditOnce() {
        stubReceiveDependencies();

        StockTransferResponse response = transferService.receiveTransfer(
                tenantId, transfer.getId(), receiveRequest(5));

        assertEquals(StockTransferStatus.COMPLETED, response.getStatus());
        assertEquals(StockTransferStatus.COMPLETED, transfer.getStatus());
        assertEquals(1, item.getDestinationAllocations().size());
        assertEquals(5, item.getDestinationAllocations().get(0).getQuantity());
        ArgumentCaptor<InventoryReceipt> receiptCaptor = ArgumentCaptor.forClass(InventoryReceipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());
        assertEquals("Source", receiptCaptor.getValue().getSenderName());
        ArgumentCaptor<InventoryReceiptItem> receiptItemCaptor =
                ArgumentCaptor.forClass(InventoryReceiptItem.class);
        verify(receiptItemRepository).save(receiptItemCaptor.capture());
        InventoryReceiptItem receiptItem = receiptItemCaptor.getValue();
        assertEquals(5, receiptItem.getQuantity());
        assertNotNull(receiptItem.getStockBatch());
        assertEquals(5, receiptItem.getStockBatch().getQuantity());
        assertNotNull(receiptItem.getStockBatch().getArrivalDate());
        verify(transactionRepository).save(any(InventoryTransaction.class));
        verify(stockBatchRepository).save(any(StockBatch.class));
        verify(transferRepository).save(transfer);
        verify(notificationService).push(
                tenantId,
                "Chuyển kho đã hoàn tất",
                "yêu cầu chuyển kho từ kho 'Source' đến kho 'Destination' đã được tiếp nhận thành công. "
                        + "Tồn kho tại kho đích đã được cập nhật.",
                "TRANSFER");
    }

    @Test
    void receiveTransfer_rejectsRetryAfterCompletionWithoutMutation() {
        transfer.setStatus(StockTransferStatus.COMPLETED);
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThrows(ResourceConflictException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(5)));

        verify(layoutRepository, never()).findByWarehouseIdAndTenantId(any(), any());
        verify(receiptRepository, never()).save(any());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void receiveTransfer_rejectsDestinationAllocationMismatchBeforeReceipt() {
        stubDestinationValidationDependencies();

        assertThrows(BadRequestException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(4)));

        verify(receiptRepository, never()).save(any());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void receiveTransfer_rejectsDestinationCapacityBeforeReceipt() {
        destinationRack.setMaxWeight(new BigDecimal("9"));
        stubDestinationValidationDependencies();
        when(rackRepository.findByIdForUpdate(rackId)).thenReturn(Optional.of(destinationRack));
        when(binRepository.findByIdForUpdate(binId)).thenReturn(Optional.of(destinationBin));
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(
                destinationWarehouseId, tenantId)).thenReturn(List.of());

        assertThrows(BadRequestException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(5)));

        verify(receiptRepository, never()).save(any());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void receiveTransfer_rejectsRackOutsideTenantSnapshot() {
        WarehouseLayout ownerLayout = WarehouseLayout.builder()
                .id(UUID.randomUUID())
                .warehouse(destinationWarehouse)
                .isDefault(true)
                .build();
        destinationRack.setLayout(ownerLayout);
        stubDestinationValidationDependencies();

        assertThrows(BadRequestException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(5)));

        verify(rackRepository, never()).findByIdForUpdate(any());
        verify(binRepository, never()).findByIdForUpdate(any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void receiveTransfer_rechecksActiveAccessBeforeDestinationMutation() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        doThrow(new ForbiddenException("Source contract expired"))
                .when(accessService).requireActiveContract(tenantId, sourceWarehouseId);

        assertThrows(ForbiddenException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(5)));

        verify(layoutRepository, never()).findByWarehouseIdAndTenantId(any(), any());
        verify(receiptRepository, never()).save(any());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void receiveTransfer_rejectsBinOutsideSelectedRack() {
        destinationBin.setRack(WarehouseRack.builder()
                .id(UUID.randomUUID())
                .layout(tenantLayout)
                .name("Other Rack")
                .build());
        stubDestinationValidationDependencies();

        assertThrows(BadRequestException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(5)));

        verify(rackRepository, never()).findByIdForUpdate(any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void receiveTransfer_hidesTransferOwnedByAnotherTenant() {
        User otherTenant = User.builder()
                .id(UUID.randomUUID())
                .roles(Set.of(Role.builder().name(RoleType.ROLE_TENANT.name()).build()))
                .build();
        transfer.setTenant(otherTenant);
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThrows(ResourceNotFoundException.class,
                () -> transferService.receiveTransfer(tenantId, transfer.getId(), receiveRequest(5)));

        verify(accessService, never()).requireActiveSubscription(any());
        verify(receiptRepository, never()).save(any());
    }

    private void stubReceiveDependencies() {
        stubDestinationValidationDependencies();
        when(rackRepository.findByIdForUpdate(rackId)).thenReturn(Optional.of(destinationRack));
        when(binRepository.findByIdForUpdate(binId)).thenReturn(Optional.of(destinationBin));
        when(stockBatchRepository.findActivePhysicalLoadsByWarehouseIdAndTenantId(
                destinationWarehouseId, tenantId)).thenReturn(List.of());
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(stockBatchRepository.save(any(StockBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubDestinationValidationDependencies() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        when(layoutRepository.findByWarehouseIdAndTenantId(destinationWarehouseId, tenantId))
                .thenReturn(Optional.of(tenantLayout));
        when(rackRepository.findByIdAndIsDeletedFalse(rackId)).thenReturn(Optional.of(destinationRack));
        when(binRepository.findByIdAndIsDeletedFalse(binId)).thenReturn(Optional.of(destinationBin));
    }

    private ReceiveStockTransferRequest receiveRequest(int quantity) {
        return ReceiveStockTransferRequest.builder()
                .destinationAllocations(List.of(StockTransferDestinationAllocationRequest.builder()
                        .itemId(itemId)
                        .destinationRackId(rackId)
                        .destinationBinId(binId)
                        .quantity(quantity)
                        .build()))
                .build();
    }
}
