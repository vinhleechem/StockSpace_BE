package fu.stockspace.stockspace_be.wms.transfer;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class StockTransferDispatchServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
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

    @InjectMocks
    private StockTransferService transferService;

    private UUID tenantId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private UUID skuId;
    private UUID batchId;
    private User tenant;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private WarehouseRack sourceRack;
    private WarehouseBin sourceBin;
    private ProductSku sku;
    private StockBatch sourceBatch;
    private StockTransfer transfer;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        Role tenantRole = Role.builder().name(RoleType.ROLE_TENANT.name()).build();
        tenant = User.builder().id(tenantId).fullName("Tenant").roles(Set.of(tenantRole)).build();
        sourceWarehouse = Warehouse.builder().id(sourceWarehouseId).name("Source").build();
        destinationWarehouse = Warehouse.builder().id(destinationWarehouseId).name("Destination").build();
        sourceRack = WarehouseRack.builder().id(UUID.randomUUID()).name("Rack").build();
        sourceBin = WarehouseBin.builder().id(UUID.randomUUID()).rack(sourceRack).name("Bin").build();
        sku = ProductSku.builder().id(skuId).tenant(tenant).skuCode("SKU-1").name("Product").build();
        sourceBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(sourceWarehouse)
                .rack(sourceRack)
                .bin(sourceBin)
                .quantity(10)
                .build();

        StockTransferItem item = StockTransferItem.builder()
                .id(UUID.randomUUID())
                .sku(sku)
                .requestedQuantity(3)
                .build();
        StockTransferSourceAllocation allocation = StockTransferSourceAllocation.builder()
                .id(UUID.randomUUID())
                .item(item)
                .sourceStockBatch(sourceBatch)
                .sourceRack(sourceRack)
                .sourceBin(sourceBin)
                .quantity(3)
                .build();
        item.setSourceAllocations(List.of(allocation));
        item.setDestinationAllocations(List.of());
        transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .createdBy(tenant)
                .status(StockTransferStatus.PENDING)
                .items(List.of(item))
                .build();
        item.setTransfer(transfer);
    }

    @Test
    void approveDispatch_locksAndDeductsSourceOnceWithAuditReceipt() {
        stubDispatchAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        when(stockBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(sourceBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = transferService.approveDispatch(tenantId, transfer.getId());

        assertEquals(StockTransferStatus.IN_TRANSIT, response.getStatus());
        assertEquals(7, sourceBatch.getQuantity());
        assertEquals(StockTransferStatus.IN_TRANSIT, transfer.getStatus());
        verify(stockBatchRepository).findByIdForUpdate(batchId);
        verify(stockBatchRepository).save(sourceBatch);
        verify(receiptRepository).save(any(InventoryReceipt.class));
        verify(receiptItemRepository).save(any());
        verify(transactionRepository).save(any());
        verify(transferRepository).save(transfer);
        verify(notificationService).push(
                tenantId,
                "Yêu cầu chuyển kho đã được duyệt xuất",
                "yêu cầu chuyển kho từ kho 'Source' đến kho 'Destination' đã được duyệt xuất và đang vận chuyển.",
                "TRANSFER");
    }

    @Test
    void approveDispatch_rejectsRetryAfterStateChangedWithoutMutatingStock() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThrows(ResourceConflictException.class,
                () -> transferService.approveDispatch(tenantId, transfer.getId()));
        verify(stockBatchRepository, never()).findByIdForUpdate(any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void approveDispatch_rechecksQuantityBeforeCreatingReceipt() {
        stubDispatchAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        sourceBatch.setQuantity(2);
        when(stockBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(sourceBatch));

        assertThrows(BadRequestException.class,
                () -> transferService.approveDispatch(tenantId, transfer.getId()));
        verify(receiptRepository, never()).save(any());
        verify(stockBatchRepository, never()).save(any());
    }

    @Test
    void approveDispatch_rechecksBothWarehouseContractsAndSubscription() {
        stubDispatchAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        when(stockBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(sourceBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transferService.approveDispatch(tenantId, transfer.getId());

        verify(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        verify(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        verify(accessService).requireActiveSubscription(tenantId);
    }

    @Test
    void approveDispatch_locksMultipleSourceBatchesInStableUuidOrder() {
        stubDispatchAccess();
        UUID lowerBatchId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherBatchId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        sourceBatch.setId(higherBatchId);
        sourceBatch.setQuantity(3);
        transfer.getItems().get(0).setRequestedQuantity(3);
        transfer.getItems().get(0).getSourceAllocations().get(0).setQuantity(3);

        WarehouseRack secondRack = WarehouseRack.builder().id(UUID.randomUUID()).name("Rack 2").build();
        WarehouseBin secondBin = WarehouseBin.builder().id(UUID.randomUUID()).rack(secondRack).name("Bin 2").build();
        StockBatch lowerBatch = StockBatch.builder()
                .id(lowerBatchId)
                .skuId(skuId)
                .warehouse(sourceWarehouse)
                .rack(secondRack)
                .bin(secondBin)
                .quantity(2)
                .build();
        StockTransferItem secondItem = StockTransferItem.builder()
                .id(UUID.randomUUID())
                .sku(sku)
                .requestedQuantity(2)
                .build();
        StockTransferSourceAllocation secondAllocation = StockTransferSourceAllocation.builder()
                .id(UUID.randomUUID())
                .item(secondItem)
                .sourceStockBatch(lowerBatch)
                .sourceRack(secondRack)
                .sourceBin(secondBin)
                .quantity(2)
                .build();
        secondItem.setSourceAllocations(List.of(secondAllocation));
        secondItem.setDestinationAllocations(List.of());
        secondItem.setTransfer(transfer);
        transfer.setItems(List.of(transfer.getItems().get(0), secondItem));

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        when(stockBatchRepository.findByIdForUpdate(lowerBatchId)).thenReturn(Optional.of(lowerBatch));
        when(stockBatchRepository.findByIdForUpdate(higherBatchId)).thenReturn(Optional.of(sourceBatch));
        when(receiptRepository.save(any(InventoryReceipt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(receiptItemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        transferService.approveDispatch(tenantId, transfer.getId());

        InOrder order = inOrder(stockBatchRepository);
        order.verify(stockBatchRepository).findByIdForUpdate(lowerBatchId);
        order.verify(stockBatchRepository).findByIdForUpdate(higherBatchId);
        assertEquals(0, lowerBatch.getQuantity());
        assertEquals(0, sourceBatch.getQuantity());
        assertEquals(StockTransferStatus.IN_TRANSIT, transfer.getStatus());
    }

    @Test
    void approveDispatch_rejectsExpiredSubscriptionBeforeStockMutation() {
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doThrow(new ForbiddenException("Subscription expired"))
                .when(accessService).requireActiveSubscription(tenantId);
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThrows(ForbiddenException.class,
                () -> transferService.approveDispatch(tenantId, transfer.getId()));

        verify(stockBatchRepository, never()).findByIdForUpdate(any());
        verify(stockBatchRepository, never()).save(any());
        verify(receiptRepository, never()).save(any());
    }

    private void stubDispatchAccess() {
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doNothing().when(accessService).requireActiveSubscription(tenantId);
    }
}
