package fu.stockspace.stockspace_be.wms.transfer;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptItemRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryReceiptRepository;
import fu.stockspace.stockspace_be.wms.receipt.repository.InventoryTransactionRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDecisionRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferDecisionServiceTest {

    @Mock
    private StockTransferRepository transferRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantMemberRepository tenantMemberRepository;
    @Mock
    private TenantWarehouseAccessService accessService;
    @Mock
    private InventoryReceiptRepository receiptRepository;
    @Mock
    private InventoryReceiptItemRepository receiptItemRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private StockBatchRepository stockBatchRepository;
    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;

    @InjectMocks
    private StockTransferService transferService;

    private UUID tenantId;
    private UUID sourceWarehouseId;
    private UUID destinationWarehouseId;
    private User tenant;
    private StockTransfer transfer;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        sourceWarehouseId = UUID.randomUUID();
        destinationWarehouseId = UUID.randomUUID();
        tenant = User.builder()
                .id(tenantId)
                .fullName("Tenant")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_TENANT.name()).build()))
                .build();
        transfer = StockTransfer.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .sourceWarehouse(Warehouse.builder().id(sourceWarehouseId).name("Source").build())
                .destinationWarehouse(Warehouse.builder().id(destinationWarehouseId).name("Destination").build())
                .createdBy(tenant)
                .status(StockTransferStatus.PENDING)
                .build();
    }

    @Test
    void rejectTransfer_setsReasonActorAndRejectedStatusWithoutStockMutation() {
        stubPendingDecision();

        StockTransferResponse response = transferService.rejectTransfer(
                tenantId, transfer.getId(), decisionRequest("Source stock is no longer required"));

        assertEquals(StockTransferStatus.REJECTED, response.getStatus());
        assertEquals(StockTransferStatus.REJECTED, transfer.getStatus());
        assertEquals("Source stock is no longer required", transfer.getDecisionReason());
        assertEquals(tenant, transfer.getRejectedBy());
        assertNotNull(transfer.getRejectedAt());
        verify(transferRepository).save(transfer);
        verify(stockBatchRepository, never()).save(any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void cancelTransfer_setsReasonActorAndCancelledStatusWithoutStockMutation() {
        stubPendingDecision();

        StockTransferResponse response = transferService.cancelTransfer(
                tenantId, transfer.getId(), decisionRequest("Created in error"));

        assertEquals(StockTransferStatus.CANCELLED, response.getStatus());
        assertEquals(StockTransferStatus.CANCELLED, transfer.getStatus());
        assertEquals("Created in error", transfer.getDecisionReason());
        assertEquals(tenant, transfer.getCancelledBy());
        assertNotNull(transfer.getCancelledAt());
        verify(transferRepository).save(transfer);
        verify(stockBatchRepository, never()).save(any());
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void decision_rejectsNonPendingTransferBeforeMutation() {
        transfer.setStatus(StockTransferStatus.IN_TRANSIT);
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThrows(ResourceConflictException.class,
                () -> transferService.rejectTransfer(
                        tenantId, transfer.getId(), decisionRequest("Not allowed after dispatch")));

        verify(accessService, never()).requireActiveSubscription(tenantId);
        verify(transferRepository, never()).save(any());
    }

    @Test
    void decision_requiresNonBlankReason() {
        stubPendingAccess();

        assertThrows(BadRequestException.class,
                () -> transferService.cancelTransfer(
                        tenantId, transfer.getId(), decisionRequest("  ")));

        verify(transferRepository, never()).save(any());
    }

    @Test
    void decision_rejectsStaffActor() {
        User staff = User.builder()
                .id(UUID.randomUUID())
                .roles(Set.of(Role.builder().name(RoleType.ROLE_STAFF.name()).build()))
                .build();
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));

        assertThrows(ForbiddenException.class,
                () -> transferService.rejectTransfer(
                        staff.getId(), transfer.getId(), decisionRequest("Staff cannot decide")));

        verify(transferRepository, never()).findByIdForUpdate(any());
        verify(transferRepository, never()).save(any());
    }

    private void stubPendingDecision() {
        stubPendingAccess();
        when(transferRepository.save(any(StockTransfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubPendingAccess() {
        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(transferRepository.findByIdForUpdate(transfer.getId())).thenReturn(Optional.of(transfer));
        doNothing().when(accessService).requireActiveContract(tenantId, sourceWarehouseId);
        doNothing().when(accessService).requireActiveContract(tenantId, destinationWarehouseId);
        doNothing().when(accessService).requireActiveSubscription(tenantId);
    }

    private StockTransferDecisionRequest decisionRequest(String reason) {
        return StockTransferDecisionRequest.builder().reason(reason).build();
    }
}
