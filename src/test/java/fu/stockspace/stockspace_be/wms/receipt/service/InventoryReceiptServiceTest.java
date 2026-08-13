package fu.stockspace.stockspace_be.wms.receipt.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private SubscriptionService subscriptionService;
    @Mock private fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository tenantMemberRepository;
    @Mock private fu.stockspace.stockspace_be.contract.repository.RentalContractRepository rentalContractRepository;
    @Mock private fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository assignmentRepository;

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

        lenient().when(rentalContractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(any(), any())).thenReturn(true);
        lenient().when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void testCreateReceipt_Success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findById(binId)).thenReturn(Optional.of(bin));

        InventoryReceipt receipt = InventoryReceipt.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .createdBy(tenantUser)
                .type(DocumentType.INBOUND)
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
                .items(List.of(itemRequest))
                .build();

        InventoryReceiptResponse response = receiptService.createReceipt(userId, request);

        assertNotNull(response);
        assertEquals(ApprovalStatus.PENDING, response.getStatus());
        assertEquals(DocumentType.INBOUND, response.getType());
        assertEquals(1, response.getItems().size());
        assertEquals("SKU123", response.getItems().get(0).getSkuCode());
        verify(receiptRepository, times(1)).save(any(InventoryReceipt.class));
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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(i -> i.getArgument(0));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(binRepository.findById(binId)).thenReturn(Optional.of(binWithLimit));
        when(stockBatchRepository.sumQuantityByBinId(binId)).thenReturn(40); // 40 existing + 20 incoming = 60 > 50

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
    void testCreateReceipt_SubscriptionRequired() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(false);

        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .warehouseId(warehouseId)
                .type(DocumentType.INBOUND)
                .build();

        assertThrows(ForbiddenException.class, () -> receiptService.createReceipt(userId, request));
    }

    @Test
    void testCreateReceipt_InvalidCoordinates_RackNotInWarehouse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));

        Warehouse alternateWarehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        WarehouseLayout alternateLayout = WarehouseLayout.builder().id(UUID.randomUUID()).warehouse(alternateWarehouse).build();
        WarehouseRack invalidRack = WarehouseRack.builder().id(rackId).layout(alternateLayout).build();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(invalidRack));

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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, userId, warehouseId,
                fu.stockspace.stockspace_be.staff.entity.AssignmentStatus.ACTIVE)).thenReturn(false);

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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(50)
                .rack(rack)
                .bin(bin)
                .build();
        when(receiptItemRepository.findByReceiptId(receipt.getId())).thenReturn(List.of(item));

        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, rackId, binId)).thenReturn(Optional.empty());

        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.approveReceipt(approverId, receipt.getId());

        assertNotNull(response);
        assertEquals(ApprovalStatus.APPROVED, response.getStatus());
        verify(stockBatchRepository, times(1)).save(any(StockBatch.class));
        verify(transactionRepository, times(1)).save(any(InventoryTransaction.class));
    }

    @Test
    void testApproveReceipt_Success_Inbound_ExistingBatch() {
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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);

        InventoryReceiptItem item = InventoryReceiptItem.builder()
                .id(UUID.randomUUID())
                .receipt(receipt)
                .sku(productSku)
                .quantity(50)
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
        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, rackId, binId)).thenReturn(Optional.of(existingBatch));

        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.approveReceipt(approverId, receipt.getId());

        assertNotNull(response);
        assertEquals(ApprovalStatus.APPROVED, response.getStatus());
        assertEquals(150, existingBatch.getQuantity());
        verify(stockBatchRepository, times(1)).save(existingBatch);
        verify(transactionRepository, times(1)).save(any(InventoryTransaction.class));
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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);

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
        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, rackId, binId)).thenReturn(Optional.of(existingBatch));

        when(receiptRepository.save(any(InventoryReceipt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryReceiptResponse response = receiptService.approveReceipt(approverId, receipt.getId());

        assertNotNull(response);
        assertEquals(ApprovalStatus.APPROVED, response.getStatus());
        assertEquals(70, existingBatch.getQuantity());
        verify(stockBatchRepository, times(1)).save(existingBatch);
        verify(transactionRepository, times(1)).save(any(InventoryTransaction.class));
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
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);

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
        when(stockBatchRepository.findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
                skuId, warehouseId, rackId, binId)).thenReturn(Optional.of(existingBatch));

        assertThrows(BadRequestException.class, () -> receiptService.approveReceipt(approverId, receipt.getId()));
    }
}
