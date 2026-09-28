package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.*;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditItemOrigin;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditItemRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditAdjustmentRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditLockRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRackRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseBinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
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
    @Mock private TenantMemberRepository tenantMemberRepository;
    @Mock private StaffWarehouseAssignmentRepository assignmentRepository;
    @Mock private TenantWarehouseAccessService accessService;
    @Mock private WarehouseRackRepository warehouseRackRepository;
    @Mock private WarehouseBinRepository warehouseBinRepository;
    @Mock private InventoryAuditLockService auditLockService;
    @Mock private InventoryAuditAdjustmentRepository auditAdjustmentRepository;

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

        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(tenantUser));
        lenient().when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));
        lenient().when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(approverId))
                .thenReturn(Optional.of(TenantMember.builder()
                        .user(approverUser)
                        .tenant(tenantUser)
                        .isActive(true)
                        .isDeleted(false)
                        .build()));
        lenient().when(tenantMemberRepository.findByUserIdOrderByJoinedAtDesc(approverId))
                .thenReturn(List.of(TenantMember.builder().user(approverUser).tenant(tenantUser).build()));
        lenient().when(accessService.findActiveContractWarehouses(userId)).thenReturn(List.of(warehouse));
    }

    @Test
    void testGetAllAudits_Admin_Success() {
        InventoryAudit audit2 = InventoryAudit.builder()
                .id(UUID.randomUUID()).warehouse(warehouse)
                .requestedBy(tenantUser).status(AuditStatus.APPROVED).build();

        Page<InventoryAudit> page = new PageImpl<>(List.of(pendingAudit, audit2),
                PageRequest.of(0, 20), 2);
        when(auditRepository.findCanonicalAudits(any(Pageable.class))).thenReturn(page);

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
        when(auditRepository.findCanonicalAudits(any(Pageable.class))).thenReturn(emptyPage);

        Pageable pageable = PageRequest.of(0, 20);
        PagedResponse<InventoryAuditResponse> response = inventoryAuditService.getAllAudits(pageable);

        assertNotNull(response);
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    void testCreateAudit_CreatesDraftWithoutSnapshot() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(auditRepository.save(any(InventoryAudit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InventoryAuditResponse response = inventoryAuditService.createAudit(userId,
                CreateInventoryAuditPlanRequest.builder().warehouseId(warehouseId).note("Cycle count").build());

        assertEquals(AuditStatus.DRAFT, response.getStatus());
        verify(stockBatchRepository, never()).findByWarehouseIdAndTenantId(any(), any(), any());
    }

    @Test
    void testStartAudit_SnapshotsAndLocksWarehouse() {
        InventoryAudit draft = InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).tenant(tenantUser).requestedBy(tenantUser)
                .status(AuditStatus.DRAFT).build();
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(draft));
        when(stockBatchRepository.findByWarehouseIdAndTenantId(eq(warehouseId), eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stockBatch)));
        when(auditItemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any(InventoryAudit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));

        InventoryAuditResponse response = inventoryAuditService.startAudit(userId, auditId);

        assertEquals(AuditStatus.IN_PROGRESS, response.getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals(AuditItemOrigin.SNAPSHOT, response.getItems().get(0).getItemOrigin());
        verify(auditLockService).acquire(draft);
    }

    @Test
    void testRequestRecountKeepsWarehouseLock() {
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(submittedAudit));
        when(auditRepository.save(any(InventoryAudit.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(auditItemRepository.findByAuditIdAndCountRoundOrderById(auditId, submittedAudit.getCountRound()))
                .thenReturn(Collections.emptyList());

        InventoryAuditResponse response = inventoryAuditService.requestRecount(
                approverId, auditId, "Kiểm tra lại chênh lệch");

        assertEquals(AuditStatus.RECOUNT_REQUIRED, response.getStatus());
        verify(auditLockService).acquire(submittedAudit);
        verify(auditLockService, never()).release(auditId);
    }

    @Test
    void testSubmitAudit_RejectsIncompleteCount() {
        InventoryAudit inProgress = InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).tenant(tenantUser).requestedBy(tenantUser)
                .status(AuditStatus.IN_PROGRESS).build();
        InventoryAuditItem uncounted = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(inProgress).batch(stockBatch)
                .expectedQuantity(100).countRound(1).build();
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(inProgress));
        when(auditItemRepository.findByAuditIdAndCountRoundOrderById(auditId, 1))
                .thenReturn(List.of(uncounted));

        assertThrows(BadRequestException.class, () -> inventoryAuditService.submitAudit(userId, auditId));
        verify(auditRepository, never()).save(any(InventoryAudit.class));
    }

    @Test
    void testApproveAudit_AbortsWhenBookQuantityChanged() {
        User counter = User.builder().id(UUID.randomUUID()).fullName("Counter").build();
        InventoryAudit submitted = InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).tenant(tenantUser).requestedBy(tenantUser)
                .status(AuditStatus.SUBMITTED).assignedTo(counter).build();
        InventoryAuditItem item = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(submitted).batch(stockBatch)
                .expectedQuantity(100).actualQuantity(95).discrepancy(-5)
                .countStatus(fu.stockspace.stockspace_be.wms.stock.entity.AuditCountStatus.COUNTED)
                .countRound(1).build();
        StockBatch changedBatch = StockBatch.builder().id(batchId).skuId(skuId)
                .warehouse(warehouse).quantity(90).build();
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(submitted));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approverUser));
        when(auditLockService.isLockedBy(auditId, warehouseId)).thenReturn(true);
        when(auditItemRepository.findByAuditIdAndCountRoundOrderById(auditId, 1)).thenReturn(List.of(item));
        when(stockBatchRepository.findByIdForUpdate(batchId)).thenReturn(Optional.of(changedBatch));

        assertThrows(RuntimeException.class, () -> inventoryAuditService.approveAudit(approverId, auditId));
        verify(inventoryReceiptService, never()).createAuditAdjustmentReceipt(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void testAddUnexpectedItem_RackScopePersistsOriginAndSelectedBin() {
        WarehouseRack rack = rackInWarehouse(warehouse, "RACK-01");
        WarehouseBin bin = binInRack(rack, "BIN-01");
        InventoryAudit audit = inProgressAudit(AuditScopeType.RACK, rack, null);
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(audit));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(productSku));
        when(warehouseBinRepository.findByIdAndIsDeletedFalse(bin.getId())).thenReturn(Optional.of(bin));
        when(auditItemRepository.findByAuditIdAndCountRoundOrderById(auditId, 1))
                .thenReturn(new ArrayList<>());

        InventoryAuditResponse response = inventoryAuditService.addUnexpectedItem(userId, auditId,
                AddUnexpectedAuditItemRequest.builder()
                        .skuId(skuId).rackId(rack.getId()).binId(bin.getId())
                        .actualQuantity(2).build());

        InventoryAuditItem saved = responseItemSavedByRepository();
        assertEquals(AuditItemOrigin.UNEXPECTED, saved.getItemOrigin());
        assertEquals(rack.getId(), saved.getRack().getId());
        assertEquals(bin.getId(), saved.getBin().getId());
        assertEquals(AuditItemOrigin.UNEXPECTED, response.getItems().get(0).getItemOrigin());
        assertEquals(skuId, response.getItems().get(0).getSkuId());
        assertEquals(rack.getId(), response.getScopeRackId());
        assertEquals(bin.getId(), response.getItems().get(0).getBinId());
    }

    @Test
    void testAddUnexpectedItem_RackScopeRequiresBin() {
        WarehouseRack rack = rackInWarehouse(warehouse, "RACK-01");
        InventoryAudit audit = inProgressAudit(AuditScopeType.RACK, rack, null);
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(audit));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> inventoryAuditService.addUnexpectedItem(userId, auditId,
                        AddUnexpectedAuditItemRequest.builder()
                                .skuId(skuId).actualQuantity(1).build()));

        assertEquals(ErrorCode.AUDIT_SCOPE_INVALID.getMessage(), exception.getMessage());
        verify(auditItemRepository, never()).save(any());
    }

    @Test
    void testAddUnexpectedItem_WarehouseScopeRejectsBinFromAnotherRack() {
        WarehouseRack selectedRack = rackInWarehouse(warehouse, "RACK-01");
        WarehouseRack otherRack = rackInWarehouse(warehouse, "RACK-02");
        WarehouseBin otherBin = binInRack(otherRack, "BIN-02");
        InventoryAudit audit = inProgressAudit(AuditScopeType.WAREHOUSE, null, null);
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(audit));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(warehouseRackRepository.findByIdAndIsDeletedFalse(selectedRack.getId()))
                .thenReturn(Optional.of(selectedRack));
        when(warehouseBinRepository.findByIdAndIsDeletedFalse(otherBin.getId()))
                .thenReturn(Optional.of(otherBin));

        assertThrows(BadRequestException.class,
                () -> inventoryAuditService.addUnexpectedItem(userId, auditId,
                        AddUnexpectedAuditItemRequest.builder()
                                .skuId(skuId).rackId(selectedRack.getId()).binId(otherBin.getId())
                                .actualQuantity(1).build()));
        verify(auditItemRepository, never()).save(any());
    }

    @Test
    void testAddUnexpectedItem_WarehouseScopeRequiresBothRackAndBin() {
        WarehouseRack rack = rackInWarehouse(warehouse, "RACK-01");
        InventoryAudit audit = inProgressAudit(AuditScopeType.WAREHOUSE, null, null);
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(audit));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));

        assertThrows(BadRequestException.class,
                () -> inventoryAuditService.addUnexpectedItem(userId, auditId,
                        AddUnexpectedAuditItemRequest.builder()
                                .skuId(skuId).rackId(rack.getId()).actualQuantity(1).build()));
        verify(warehouseRackRepository, never()).findByIdAndIsDeletedFalse(any());
        verify(auditItemRepository, never()).save(any());
    }

    @Test
    void testAddUnexpectedItem_DuplicateSkuAndLocationReturnsSpecificConflict() {
        WarehouseRack rack = rackInWarehouse(warehouse, "RACK-01");
        WarehouseBin bin = binInRack(rack, "BIN-01");
        InventoryAudit audit = inProgressAudit(AuditScopeType.BIN, null, bin);
        InventoryAuditItem existing = InventoryAuditItem.builder()
                .id(UUID.randomUUID()).audit(audit).skuId(skuId).rack(rack).bin(bin)
                .expectedQuantity(4).countRound(1).build();
        when(auditRepository.findByIdForUpdate(auditId)).thenReturn(Optional.of(audit));
        when(productSkuRepository.findByIdAndTenantIdOrSystemAndIsDeletedFalse(skuId, userId))
                .thenReturn(Optional.of(productSku));
        when(auditItemRepository.findByAuditIdAndCountRoundOrderById(auditId, 1))
                .thenReturn(new ArrayList<>(List.of(existing)));

        ResourceConflictException exception = assertThrows(ResourceConflictException.class,
                () -> inventoryAuditService.addUnexpectedItem(userId, auditId,
                        AddUnexpectedAuditItemRequest.builder()
                                .skuId(skuId).actualQuantity(2).build()));

        assertEquals(ErrorCode.AUDIT_ITEM_DUPLICATE, exception.getErrorCode());
        verify(auditItemRepository, never()).save(any());
    }

    private InventoryAuditItem responseItemSavedByRepository() {
        org.mockito.ArgumentCaptor<InventoryAuditItem> captor =
                org.mockito.ArgumentCaptor.forClass(InventoryAuditItem.class);
        verify(auditItemRepository).save(captor.capture());
        return captor.getValue();
    }

    private InventoryAudit inProgressAudit(
            AuditScopeType scopeType, WarehouseRack scopeRack, WarehouseBin scopeBin) {
        return InventoryAudit.builder()
                .id(auditId).warehouse(warehouse).tenant(tenantUser).requestedBy(tenantUser)
                .scopeType(scopeType).scopeRack(scopeRack).scopeBin(scopeBin)
                .status(AuditStatus.IN_PROGRESS).countRound(1).build();
    }

    private WarehouseRack rackInWarehouse(Warehouse targetWarehouse, String code) {
        WarehouseLayout layout = WarehouseLayout.builder()
                .id(UUID.randomUUID()).warehouse(targetWarehouse).build();
        return WarehouseRack.builder()
                .id(UUID.randomUUID()).layout(layout).name(code).code(code).build();
    }

    private WarehouseBin binInRack(WarehouseRack rack, String code) {
        return WarehouseBin.builder()
                .id(UUID.randomUUID()).rack(rack).name(code).code(code).build();
    }
}
