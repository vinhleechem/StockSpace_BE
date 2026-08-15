package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.*;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditItemRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAuditServiceTest {

    @Mock private InventoryAuditRepository auditRepository;
    @Mock private InventoryAuditItemRepository auditItemRepository;
    @Mock private StockBatchRepository stockBatchRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductSkuRepository productSkuRepository;
    @Mock private InventoryReceiptService inventoryReceiptService;
    @Mock private NotificationService notificationService;
    @Mock private SubscriptionService subscriptionService;
    @Mock private TenantMemberRepository tenantMemberRepository;


    @InjectMocks
    private InventoryAuditService inventoryAuditService;

    private UUID userId;
    private UUID approverId;
    private UUID warehouseId;
    private UUID auditId;
    private UUID batchId;
    private UUID skuId;

    private User tenantUser;
    private User approverUser;
    private Warehouse warehouse;
    private ProductSku productSku;
    private UnitOfMeasure uom;
    private StockBatch stockBatch;
    private InventoryAudit pendingAudit;
    private InventoryAudit submittedAudit;
    private InventoryAuditItem auditItem;

    @BeforeEach
    void setUp() {
        userId     = UUID.randomUUID();
        approverId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        auditId    = UUID.randomUUID();
        batchId    = UUID.randomUUID();
        skuId      = UUID.randomUUID();

        tenantUser = User.builder()
                .id(userId)
                .email("tenant@test.com")
                .fullName("Nguyễn Văn Tenant")
                .build();

        approverUser = User.builder()
                .id(approverId)
                .email("manager@test.com")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_TENANT.name()).build()))
                .fullName("Trần Thị Manager")
                .build();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Kho Hà Nội")
                .build();

        uom = UnitOfMeasure.builder()
                .id(UUID.randomUUID())
                .name("Thùng")
                .code("thung")
                .build();

        productSku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-THUNG-001")
                .name("Nước khoáng LaVie 24 chai")
                .uom(uom)
                .build();

        stockBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(warehouse)
                .quantity(100)
                .build();

        pendingAudit = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .status(AuditStatus.PENDING)
                .note("Kiểm kê cuối tháng")
                .build();

        submittedAudit = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .status(AuditStatus.SUBMITTED)
                .note("Kiểm kê cuối tháng")
                .build();

        auditItem = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(pendingAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .actualQuantity(null)
                .discrepancy(null)
                .build();
    }



    @Test
    void testCreateAudit_Success_SnapshotsCurrentStock() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(pendingAudit);

        Page<StockBatch> stockPage = new PageImpl<>(List.of(stockBatch));
        when(stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(eq(warehouseId), any(Pageable.class)))
                .thenReturn(stockPage);

        InventoryAuditItem savedItem = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(pendingAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .actualQuantity(null)
                .discrepancy(null)
                .build();
        when(auditItemRepository.saveAll(anyList())).thenReturn(List.of(savedItem));

        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        CreateInventoryAuditRequest request = CreateInventoryAuditRequest.builder()
                .warehouseId(warehouseId)
                .note("Kiểm kê cuối tháng")
                .build();

        InventoryAuditResponse response = inventoryAuditService.createAudit(userId, request);

        assertNotNull(response);
        assertEquals(AuditStatus.PENDING, response.getStatus());
        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(1, response.getItems().size());

        assertEquals(100, response.getItems().get(0).getExpectedQuantity());
        verify(auditItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testCreateAudit_SubscriptionRequired() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(false);

        CreateInventoryAuditRequest request = CreateInventoryAuditRequest.builder()
                .warehouseId(warehouseId)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> inventoryAuditService.createAudit(userId, request));

        assertEquals(ErrorCode.SUBSCRIPTION_REQUIRED.getMessage(), ex.getMessage());
    }

    @Test
    void testCreateAudit_WarehouseNotFound() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        CreateInventoryAuditRequest request = CreateInventoryAuditRequest.builder()
                .warehouseId(warehouseId)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> inventoryAuditService.createAudit(userId, request));
    }

    @Test
    void testCreateAudit_EmptyWarehouse_ZeroItems() {
        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(pendingAudit);

        Page<StockBatch> emptyPage = new PageImpl<>(Collections.emptyList());
        when(stockBatchRepository.findByWarehouseIdAndIsDeletedFalse(eq(warehouseId), any(Pageable.class)))
                .thenReturn(emptyPage);
        when(auditItemRepository.saveAll(anyList())).thenReturn(Collections.emptyList());

        CreateInventoryAuditRequest request = CreateInventoryAuditRequest.builder()
                .warehouseId(warehouseId)
                .build();

        InventoryAuditResponse response = inventoryAuditService.createAudit(userId, request);

        assertNotNull(response);
        assertTrue(response.getItems().isEmpty());
    }



    @Test
    void testSubmitAudit_Success_CalculatesDiscrepancy() {

        when(auditRepository.findById(auditId)).thenReturn(Optional.of(pendingAudit));

        InventoryAuditItem mutableItem = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(pendingAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .actualQuantity(null)
                .discrepancy(null)
                .build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(mutableItem));
        when(auditItemRepository.save(any(InventoryAuditItem.class))).thenReturn(mutableItem);

        InventoryAudit submittedAuditMock = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .status(AuditStatus.SUBMITTED)
                .build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(submittedAuditMock);

        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        SubmitAuditItemRequest itemReq = SubmitAuditItemRequest.builder()
                .batchId(batchId)
                .actualQuantity(85)
                .note("Mất 15 thùng")
                .build();

        SubmitAuditRequest request = SubmitAuditRequest.builder()
                .items(List.of(itemReq))
                .build();

        InventoryAuditResponse response = inventoryAuditService.submitAudit(userId, auditId, request);

        assertNotNull(response);
        assertEquals(AuditStatus.SUBMITTED, response.getStatus());

        assertEquals(85, mutableItem.getActualQuantity());
        assertEquals(-15, mutableItem.getDiscrepancy());
    }

    @Test
    void testSubmitAudit_SurplusStock_PositiveDiscrepancy() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(pendingAudit));

        InventoryAuditItem mutableItem = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(pendingAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(mutableItem));
        when(auditItemRepository.save(any(InventoryAuditItem.class))).thenReturn(mutableItem);
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(submittedAudit);
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        SubmitAuditItemRequest itemReq = SubmitAuditItemRequest.builder()
                .batchId(batchId)
                .actualQuantity(110)
                .build();

        InventoryAuditResponse response = inventoryAuditService.submitAudit(userId, auditId,
                SubmitAuditRequest.builder().items(List.of(itemReq)).build());

        assertNotNull(response);
        assertEquals(10, mutableItem.getDiscrepancy());
    }

    @Test
    void testSubmitAudit_WrongStatus_ThrowsBadRequest() {

        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));


        SubmitAuditRequest request = SubmitAuditRequest.builder()
                .items(List.of())
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventoryAuditService.submitAudit(userId, auditId, request));

        assertEquals(ErrorCode.AUDIT_INVALID_STATUS.getMessage(), ex.getMessage());
    }



    @Test
    void testApproveAudit_StaffApprover_ThrowsForbidden() {
        approverUser.setRoles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()));
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));

        assertThrows(ForbiddenException.class,
                () -> inventoryAuditService.approveAudit(approverId, auditId));

        verify(auditRepository, never()).save(any(InventoryAudit.class));
        verifyNoInteractions(inventoryReceiptService, notificationService);
    }

    @Test
    void testApproveAudit_AdminApprover_ThrowsForbidden() {
        approverUser.setRoles(Set.of(Role.builder().name(RoleType.ROLE_ADMIN.name()).build()));
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));

        assertThrows(ForbiddenException.class,
                () -> inventoryAuditService.approveAudit(approverId, auditId));

        verify(auditRepository, never()).save(any(InventoryAudit.class));
        verifyNoInteractions(inventoryReceiptService, notificationService);
    }

    @Test
    void testApproveAudit_WithDiscrepancy_CreatesAdjustmentReceipt() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));


        InventoryAuditItem itemWithDeficit = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(submittedAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .actualQuantity(85)
                .discrepancy(-15)
                .build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(itemWithDeficit));

        InventoryAudit approvedAudit = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .approvedBy(approverUser)
                .status(AuditStatus.APPROVED)
                .build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(approvedAudit);
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        InventoryAuditResponse response = inventoryAuditService.approveAudit(approverId, auditId);

        assertNotNull(response);
        assertEquals(AuditStatus.APPROVED, response.getStatus());


        verify(inventoryReceiptService, times(1)).createAdjustmentReceipt(
                eq(approverId),
                eq(auditId),
                eq(warehouseId),
                eq(DocumentType.OUTBOUND),
                eq(batchId),
                eq(15)
        );


        verify(notificationService, times(1)).push(
                eq(userId),
                anyString(),
                anyString(),
                eq("AUDIT")
        );
    }

    @Test
    void testApproveAudit_WithSurplus_CreatesInboundReceipt() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));


        InventoryAuditItem surplusItem = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(submittedAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .actualQuantity(110)
                .discrepancy(10)
                .build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(surplusItem));

        InventoryAudit approvedAudit = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .approvedBy(approverUser)
                .status(AuditStatus.APPROVED)
                .build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(approvedAudit);
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        inventoryAuditService.approveAudit(approverId, auditId);

        verify(inventoryReceiptService, times(1)).createAdjustmentReceipt(
                eq(approverId),
                eq(auditId),
                eq(warehouseId),
                eq(DocumentType.INBOUND),
                eq(batchId),
                eq(10)
        );
    }

    @Test
    void testApproveAudit_NoDiscrepancy_DoesNotCreateReceipt() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));


        InventoryAuditItem exactItem = InventoryAuditItem.builder()
                .id(UUID.randomUUID())
                .audit(submittedAudit)
                .batch(stockBatch)
                .expectedQuantity(100)
                .actualQuantity(100)
                .discrepancy(0)
                .build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(exactItem));

        InventoryAudit approvedAudit = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .approvedBy(approverUser)
                .status(AuditStatus.APPROVED)
                .build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(approvedAudit);
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        inventoryAuditService.approveAudit(approverId, auditId);


        verify(inventoryReceiptService, never()).createAdjustmentReceipt(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void testApproveAudit_WrongStatus_ThrowsBadRequest() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(pendingAudit));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventoryAuditService.approveAudit(approverId, auditId));

        assertEquals(ErrorCode.AUDIT_INVALID_STATUS.getMessage(), ex.getMessage());
    }

    @Test
    void testApproveAudit_MultipleItems_MixedDiscrepancy() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));

        UUID batchId2 = UUID.randomUUID();
        UUID skuId2 = UUID.randomUUID();
        StockBatch stockBatch2 = StockBatch.builder()
                .id(batchId2)
                .skuId(skuId2)
                .warehouse(warehouse)
                .quantity(50)
                .build();


        InventoryAuditItem item1 = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(submittedAudit).batch(stockBatch)
                .expectedQuantity(100).actualQuantity(95).discrepancy(-5).build();


        InventoryAuditItem item2 = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(submittedAudit).batch(stockBatch2)
                .expectedQuantity(50).actualQuantity(58).discrepancy(8).build();


        InventoryAuditItem item3 = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(submittedAudit).batch(stockBatch)
                .expectedQuantity(20).actualQuantity(20).discrepancy(null).build();

        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(item1, item2, item3));

        InventoryAudit approvedAudit = InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).requestedBy(tenantUser)
                .approvedBy(approverUser).status(AuditStatus.APPROVED).build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(approvedAudit);
        when(productSkuRepository.findByIdAndIsDeletedFalse(any())).thenReturn(Optional.of(productSku));

        inventoryAuditService.approveAudit(approverId, auditId);


        verify(inventoryReceiptService, times(1)).createAdjustmentReceipt(
                any(), any(), any(), eq(DocumentType.OUTBOUND), eq(batchId), eq(5));
        verify(inventoryReceiptService, times(1)).createAdjustmentReceipt(
                any(), any(), any(), eq(DocumentType.INBOUND), eq(batchId2), eq(8));
        verify(inventoryReceiptService, times(2)).createAdjustmentReceipt(
                any(), any(), any(), any(), any(), anyInt());
    }



    @Test
    void testRejectAudit_StaffApprover_ThrowsForbidden() {
        approverUser.setRoles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()));
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));

        assertThrows(ForbiddenException.class,
                () -> inventoryAuditService.rejectAudit(approverId, auditId, "Kiểm lại"));

        verify(auditRepository, never()).save(any(InventoryAudit.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void testRejectAudit_Success_WithReason() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(submittedAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));

        InventoryAudit rejectedAudit = InventoryAudit.builder()
                .id(auditId)
                .warehouse(warehouse)
                .requestedBy(tenantUser)
                .approvedBy(approverUser)
                .status(AuditStatus.REJECTED)
                .note("Kiểm kê cuối tháng | Lý do từ chối: Số liệu không khớp")
                .build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(rejectedAudit);

        InventoryAuditItem item = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(submittedAudit).batch(stockBatch)
                .expectedQuantity(100).build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(item));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        InventoryAuditResponse response = inventoryAuditService.rejectAudit(
                approverId, auditId, "Số liệu không khớp");

        assertNotNull(response);
        assertEquals(AuditStatus.REJECTED, response.getStatus());


        verify(notificationService, times(1)).push(
                eq(userId),
                anyString(),
                anyString(),
                eq("AUDIT")
        );


        verify(inventoryReceiptService, never()).createAdjustmentReceipt(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void testRejectAudit_PendingStatus_Success() {

        when(auditRepository.findById(auditId)).thenReturn(Optional.of(pendingAudit));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));

        InventoryAudit rejectedAudit = InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).requestedBy(tenantUser)
                .status(AuditStatus.REJECTED).build();
        when(auditRepository.save(any(InventoryAudit.class))).thenReturn(rejectedAudit);

        InventoryAuditItem item = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(pendingAudit).batch(stockBatch)
                .expectedQuantity(100).build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(item));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        InventoryAuditResponse response = inventoryAuditService.rejectAudit(approverId, auditId, null);

        assertNotNull(response);
        assertEquals(AuditStatus.REJECTED, response.getStatus());
    }

    @Test
    void testRejectAudit_AlreadyApproved_ThrowsBadRequest() {
        InventoryAudit approvedAudit = InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).requestedBy(tenantUser)
                .status(AuditStatus.APPROVED).build();
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(approvedAudit));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> inventoryAuditService.rejectAudit(approverId, auditId, "Lý do gì đó"));

        assertEquals(ErrorCode.AUDIT_ALREADY_PROCESSED.getMessage(), ex.getMessage());
    }



    @Test
    void testGetMyAudits_Success_Paginated() {
        Page<InventoryAudit> page = new PageImpl<>(List.of(pendingAudit, submittedAudit),
                PageRequest.of(0, 10), 2);
        when(auditRepository.findByRequestedByIdAndIsDeletedFalse(eq(userId), any(Pageable.class)))
                .thenReturn(page);

        InventoryAuditItem item = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(pendingAudit).batch(stockBatch)
                .expectedQuantity(100).build();
        when(auditItemRepository.findByAuditId(any())).thenReturn(List.of(item));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        Pageable pageable = PageRequest.of(0, 10);
        PagedResponse<InventoryAuditResponse> response = inventoryAuditService.getMyAudits(userId, pageable);

        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        assertEquals(2, response.getTotalElements());
    }

    @Test
    void testGetMyAudits_Empty() {
        Page<InventoryAudit> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditRepository.findByRequestedByIdAndIsDeletedFalse(eq(userId), any(Pageable.class)))
                .thenReturn(emptyPage);

        Pageable pageable = PageRequest.of(0, 10);
        PagedResponse<InventoryAuditResponse> response = inventoryAuditService.getMyAudits(userId, pageable);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
    }



    @Test
    void testGetAuditDetail_Success_Requester() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(pendingAudit));

        InventoryAuditItem item = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(pendingAudit).batch(stockBatch)
                .expectedQuantity(100).build();
        when(auditItemRepository.findByAuditId(auditId)).thenReturn(List.of(item));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));


        InventoryAuditResponse response = inventoryAuditService.getAuditDetail(userId, auditId);

        assertNotNull(response);
        assertEquals(auditId, response.getId());
        assertEquals(AuditStatus.PENDING, response.getStatus());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void testGetAuditDetail_Forbidden_NotOwner() {
        UUID strangerUserId = UUID.randomUUID();
        when(auditRepository.findById(auditId)).thenReturn(Optional.of(pendingAudit));


        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> inventoryAuditService.getAuditDetail(strangerUserId, auditId));

        assertEquals(ErrorCode.FORBIDDEN.getMessage(), ex.getMessage());
    }

    @Test
    void testGetAuditDetail_AuditNotFound() {
        when(auditRepository.findById(auditId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> inventoryAuditService.getAuditDetail(userId, auditId));
    }



    @Test
    void testGetAllAudits_Admin_Success() {
        InventoryAudit audit2 = InventoryAudit.builder()
                .id(UUID.randomUUID()).warehouse(warehouse)
                .requestedBy(tenantUser).status(AuditStatus.APPROVED).build();

        Page<InventoryAudit> page = new PageImpl<>(List.of(pendingAudit, audit2),
                PageRequest.of(0, 20), 2);
        when(auditRepository.findByIsDeletedFalse(any(Pageable.class))).thenReturn(page);

        InventoryAuditItem item = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(pendingAudit).batch(stockBatch)
                .expectedQuantity(100).build();
        when(auditItemRepository.findByAuditId(any())).thenReturn(List.of(item));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        Pageable pageable = PageRequest.of(0, 20);
        PagedResponse<InventoryAuditResponse> response = inventoryAuditService.getAllAudits(pageable);

        assertNotNull(response);
        assertEquals(2, response.getContent().size());
        assertEquals(2, response.getTotalElements());
    }

    @Test
    void testGetAllAudits_Admin_EmptySystem() {
        Page<InventoryAudit> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditRepository.findByIsDeletedFalse(any(Pageable.class))).thenReturn(emptyPage);

        Pageable pageable = PageRequest.of(0, 20);
        PagedResponse<InventoryAuditResponse> response = inventoryAuditService.getAllAudits(pageable);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
    }
}

