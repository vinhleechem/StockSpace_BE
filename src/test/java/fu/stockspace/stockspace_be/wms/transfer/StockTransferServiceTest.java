package fu.stockspace.stockspace_be.wms.transfer;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.dto.AssignStockTransferDestinationStaffRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.CreateStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferItemRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferSourceAllocationRequest;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttempt;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferAttemptRepository;
import fu.stockspace.stockspace_be.wms.transfer.repository.StockTransferRepository;
import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private StockTransferAttemptRepository attemptRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductSkuRepository productSkuRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
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
    private UUID rackId;
    private UUID binId;
    private UUID skuId;
    private UUID batchId;
    private User tenant;
    private Warehouse sourceWarehouse;
    private Warehouse destinationWarehouse;
    private WarehouseRack sourceRack;
    private WarehouseBin sourceBin;
    private ProductSku sku;
    private StockBatch sourceBatch;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();
        rackId = UUID.randomUUID();
        binId = UUID.randomUUID();
        skuId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        Role tenantRole = Role.builder().name(RoleType.ROLE_TENANT.name()).build();
        tenant = User.builder()
                .id(tenantId)
                .fullName("Tenant User")
                .roles(Set.of(tenantRole))
                .build();
        sourceWarehouse = Warehouse.builder().id(sourceWarehouseId).name("Source Warehouse").build();
        destinationWarehouse = Warehouse.builder().id(destinationWarehouseId).name("Destination Warehouse").build();
        sourceRack = WarehouseRack.builder().id(rackId).name("Rack A").build();
        sourceBin = WarehouseBin.builder().id(binId).rack(sourceRack).name("Bin A").build();
        sku = ProductSku.builder()
                .id(skuId)
                .tenant(tenant)
                .skuCode("SKU-001")
                .name("Product 1")
                .build();
        sourceBatch = StockBatch.builder()
                .id(batchId)
                .skuId(skuId)
                .warehouse(sourceWarehouse)
                .rack(sourceRack)
                .bin(sourceBin)
                .quantity(20)
                .build();
    }

    @Test
    void createTransfer_createsPendingWithoutChangingStock() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = transferService.createTransfer(tenantId,
                request(10, 10));

        assertEquals(StockTransferStatus.PENDING, response.getStatus());
        assertEquals(sourceWarehouseId, response.getSourceWarehouse().getId());
        assertEquals(destinationWarehouseId, response.getDestinationWarehouse().getId());
        assertEquals(1, response.getItems().size());
        assertEquals(10, response.getItems().get(0).getRequestedQuantity());
        assertEquals(10, response.getItems().get(0).getSourceAllocations().get(0).getQuantity());
        assertTrue(response.getItems().get(0).getDestinationAllocations().isEmpty());
        assertEquals(20, sourceBatch.getQuantity());
        verify(stockBatchRepository, never()).save(any());
        verify(notificationService, never()).push(any(), any(), any(), any());
    }

    @Test
    void createTransfer_createdByStaff_notifiesTenant() {
        UUID staffId = UUID.randomUUID();
        User staff = User.builder()
                .id(staffId)
                .fullName("Warehouse Staff")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        TenantMember membership = TenantMember.builder().user(staff).tenant(tenant).build();
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId))
                .thenReturn(Optional.of(membership));
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doNothing().when(accessService).requireActiveSubscription(tenantId);
        doNothing().when(accessService).requireActiveStaffAssignment(staffId, tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveStaffAssignment(staffId, tenantId, destinationWarehouseId);

        transferService.createTransfer(staffId, request(10, 10));

        verify(notificationService).push(
                tenantId,
                "Yêu cầu chuyển kho mới",
                "Nhân viên Warehouse Staff đã tạo yêu cầu chuyển kho từ kho 'Source Warehouse' "
                        + "đến kho 'Destination Warehouse' và đang chờ bạn duyệt xuất.",
                "TRANSFER");
    }

    @Test
    void createTransfer_assignsSourceStaffForPicking() {
        stubCreateAccess();
        UUID sourceStaffId = UUID.randomUUID();
        User sourceStaff = User.builder()
                .id(sourceStaffId)
                .fullName("Source Picker")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(sourceStaffId)).thenReturn(Optional.of(sourceStaff));
        when(tenantMemberRepository.existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(
                sourceStaffId, tenantId)).thenReturn(true);
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                sourceStaffId, tenantId, sourceWarehouseId, AssignmentStatus.ACTIVE)).thenReturn(true);
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = transferService.createTransfer(tenantId,
                request(10, 10, sourceStaffId));

        assertEquals(sourceStaffId, response.getSourceStaff().getId());
        verify(assignmentRepository).existsActiveByStaffAndTenantAndWarehouse(
                sourceStaffId, tenantId, sourceWarehouseId, AssignmentStatus.ACTIVE);
    }

    @Test
    void createTransfer_assignsDestinationStaffForReceiving() {
        stubCreateAccess();
        UUID destinationStaffId = UUID.randomUUID();
        User destinationStaff = User.builder()
                .id(destinationStaffId)
                .fullName("Destination Receiver")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(destinationStaffId)).thenReturn(Optional.of(destinationStaff));
        when(tenantMemberRepository.existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(
                destinationStaffId, tenantId)).thenReturn(true);
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                destinationStaffId, tenantId, destinationWarehouseId, AssignmentStatus.ACTIVE)).thenReturn(true);
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = transferService.createTransfer(
                tenantId, request(10, 10, null, destinationStaffId));

        assertEquals(destinationStaffId, response.getDestinationStaff().getId());
        ArgumentCaptor<StockTransferAttempt> attemptCaptor =
                ArgumentCaptor.forClass(StockTransferAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertEquals(destinationStaffId, attemptCaptor.getValue().getDestinationStaff().getId());
        verify(assignmentRepository).existsActiveByStaffAndTenantAndWarehouse(
                destinationStaffId, tenantId, destinationWarehouseId, AssignmentStatus.ACTIVE);
    }

    @Test
    void assignDestinationStaff_reassignsCurrentReceiver() {
        stubCreateAccess();
        UUID destinationStaffId = UUID.randomUUID();
        User destinationStaff = User.builder()
                .id(destinationStaffId)
                .fullName("Destination Receiver")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        StockTransfer transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .activeDestinationWarehouse(destinationWarehouse)
                .createdBy(tenant)
                .status(StockTransferStatus.IN_TRANSIT)
                .build();
        StockTransferAttempt attempt = StockTransferAttempt.builder()
                .id(UUID.randomUUID())
                .transfer(transfer)
                .sequenceNo(1)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .plannedQuantity(10)
                .build();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.findById(destinationStaffId)).thenReturn(Optional.of(destinationStaff));
        when(tenantMemberRepository.existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(
                destinationStaffId, tenantId)).thenReturn(true);
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                destinationStaffId, tenantId, destinationWarehouseId, AssignmentStatus.ACTIVE)).thenReturn(true);
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        when(attemptRepository.findTopByTransferIdOrderBySequenceNoDesc(transfer.getId()))
                .thenReturn(Optional.of(attempt));
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockTransferResponse response = transferService.assignDestinationStaff(
                tenantId,
                transfer.getId(),
                AssignStockTransferDestinationStaffRequest.builder()
                        .destinationStaffId(destinationStaffId)
                        .reason("Assign receiving shift")
                        .build(),
                "assign-key");

        assertEquals(destinationStaffId, response.getDestinationStaff().getId());
        assertEquals(destinationStaff, transfer.getDestinationStaff());
        assertEquals(destinationStaff, attempt.getDestinationStaff());
    }

    @Test
    void createTransfer_rejectsAllocationTotalThatDoesNotMatchItemQuantity() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));

        assertThrows(BadRequestException.class,
                () -> transferService.createTransfer(tenantId, request(10, 9)));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_rejectsBatchFromAnotherWarehouse() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        sourceBatch.setWarehouse(destinationWarehouse);
        when(stockBatchRepository.findByIdAndIsDeletedFalse(batchId)).thenReturn(Optional.of(sourceBatch));

        assertThrows(BadRequestException.class,
                () -> transferService.createTransfer(tenantId, request(10, 10)));
        verify(transferRepository, never()).save(any());
    }

    @Test
    void createTransfer_rejectsSystemOrAnotherTenantSku() {
        stubCreateAccess();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        ProductSku systemSku = ProductSku.builder().id(skuId).skuCode("SYSTEM-001").name("System SKU").build();
        when(productSkuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(systemSku));

        assertThrows(ResourceNotFoundException.class,
                () -> transferService.createTransfer(tenantId, request(10, 10)));
        verify(stockBatchRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void getTransfers_isTenantScopedAndSupportsStatusFilter() {
        StockTransfer transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .createdBy(tenant)
                .status(StockTransferStatus.PENDING)
                .build();
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.search(tenantId, sourceWarehouseId, destinationWarehouseId,
                StockTransferStatus.PENDING, null, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(transfer)));

        var response = transferService.getTransfers(tenantId, sourceWarehouseId,
                destinationWarehouseId, StockTransferStatus.PENDING, PageRequest.of(0, 10));

        assertEquals(1, response.getTotalElements());
        assertEquals(StockTransferStatus.PENDING, response.getContent().get(0).getStatus());
        verify(transferRepository).search(tenantId, sourceWarehouseId, destinationWarehouseId,
                StockTransferStatus.PENDING, null, PageRequest.of(0, 10));
    }

    @Test
    void getTransfer_returnsNotFoundForAnotherTenant() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        UUID transferId = UUID.randomUUID();
        when(transferRepository.findByIdAndTenantIdAndIsDeletedFalse(transferId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> transferService.getTransfer(tenantId, transferId));
    }

    @Test
    void getTransfer_allowsDualAssignedStaffWhenOnlyDestinationAssignmentRemains() {
        UUID staffId = UUID.randomUUID();
        User staff = User.builder()
                .id(staffId)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        StockTransfer transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(sourceWarehouse)
                .destinationWarehouse(destinationWarehouse)
                .activeDestinationWarehouse(destinationWarehouse)
                .sourceStaff(staff)
                .destinationStaff(staff)
                .createdBy(tenant)
                .status(StockTransferStatus.IN_TRANSIT)
                .build();
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId))
                .thenReturn(Optional.of(TenantMember.builder().user(staff).tenant(tenant).build()));
        when(transferRepository.findByIdAndTenantIdAndIsDeletedFalse(transfer.getId(), tenantId))
                .thenReturn(Optional.of(transfer));
        when(accessService.hasActiveStaffAssignment(staffId, tenantId, sourceWarehouseId))
                .thenReturn(false);
        when(accessService.hasActiveStaffAssignment(staffId, tenantId, destinationWarehouseId))
                .thenReturn(true);

        StockTransferResponse response = transferService.getTransfer(staffId, transfer.getId());

        assertEquals(transfer.getId(), response.getId());
        verify(accessService).hasActiveStaffAssignment(staffId, tenantId, sourceWarehouseId);
        verify(accessService).hasActiveStaffAssignment(staffId, tenantId, destinationWarehouseId);
    }

    @Test
    void createTransfer_requiresStaffAssignmentAtBothWarehouses() {
        UUID staffId = UUID.randomUUID();
        User staff = User.builder()
                .id(staffId)
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        TenantMember membership = TenantMember.builder().user(staff).tenant(tenant).build();
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId))
                .thenReturn(Optional.of(membership));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doNothing().when(accessService).requireActiveSubscription(tenantId);
        doNothing().when(accessService)
                .requireActiveStaffAssignment(staffId, tenantId, sourceWarehouseId);
        doThrow(new ForbiddenException(ErrorCode.FORBIDDEN)).when(accessService)
                .requireActiveStaffAssignment(staffId, tenantId, destinationWarehouseId);

        assertThrows(ForbiddenException.class,
                () -> transferService.createTransfer(staffId, request(10, 10)));

        verify(transferRepository, never()).save(any());
        verify(productSkuRepository, never()).findByIdAndIsDeletedFalse(any());
    }

    @Test
    void createTransfer_rejectsExpiredSubscriptionBeforeCreatingDraft() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(warehouseRepository.findById(sourceWarehouseId)).thenReturn(Optional.of(sourceWarehouse));
        when(warehouseRepository.findById(destinationWarehouseId)).thenReturn(Optional.of(destinationWarehouse));
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        org.mockito.Mockito.doThrow(new ForbiddenException("Subscription expired"))
                .when(accessService).requireActiveSubscription(tenantId);

        assertThrows(ForbiddenException.class,
                () -> transferService.createTransfer(tenantId, request(10, 10)));

        verify(transferRepository, never()).save(any());
    }

    private void stubCreateAccess() {
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doNothing().when(accessService).requireActiveSubscription(tenantId);
    }

    private CreateStockTransferRequest request(int requestedQuantity, int sourceQuantity) {
        return request(requestedQuantity, sourceQuantity, null);
    }

    private CreateStockTransferRequest request(int requestedQuantity, int sourceQuantity,
                                               UUID sourceStaffId) {
        return request(requestedQuantity, sourceQuantity, sourceStaffId, null);
    }

    private CreateStockTransferRequest request(int requestedQuantity, int sourceQuantity,
                                               UUID sourceStaffId, UUID destinationStaffId) {
        return CreateStockTransferRequest.builder()
                .sourceWarehouseId(sourceWarehouseId)
                .destinationWarehouseId(destinationWarehouseId)
                .sourceStaffId(sourceStaffId)
                .destinationStaffId(destinationStaffId)
                .items(List.of(StockTransferItemRequest.builder()
                        .skuId(skuId)
                        .requestedQuantity(requestedQuantity)
                        .sourceAllocations(List.of(StockTransferSourceAllocationRequest.builder()
                                .sourceStockBatchId(batchId)
                                .sourceRackId(rackId)
                                .sourceBinId(binId)
                                .quantity(sourceQuantity)
                                .build()))
                        .build()))
                .build();
    }
}
