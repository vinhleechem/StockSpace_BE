package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantWarehouseAccessServiceTest {

    @Mock
    private RentalContractRepository contractRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;

    @InjectMocks
    private TenantWarehouseAccessService accessService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();

    @Test
    void activeContractAllowsObservationWithoutSubscription() {
        when(contractRepository.existsCurrentDirectActiveContract(
                eq(tenantId), eq(warehouseId), any(LocalDate.class))).thenReturn(true);

        assertDoesNotThrow(() -> accessService.requireActiveContract(tenantId, warehouseId));
        assertTrue(accessService.canObserveWarehouse(tenantId, warehouseId));
        verify(contractRepository, times(2)).existsCurrentDirectActiveContract(
                eq(tenantId), eq(warehouseId), any(LocalDate.class));
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void activeContractAndCurrentSubscriptionAllowWmsAccess() {
        when(contractRepository.existsCurrentDirectActiveContract(
                eq(tenantId), eq(warehouseId), any(LocalDate.class))).thenReturn(true);
        when(subscriptionRepository.findCurrentByTenantIdAndStatus(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(mock(Subscription.class)));

        assertDoesNotThrow(() -> accessService.requireWmsAccess(tenantId, warehouseId));
    }

    @Test
    void activeContractWithoutCurrentSubscriptionBlocksWmsAccess() {
        when(contractRepository.existsCurrentDirectActiveContract(
                eq(tenantId), eq(warehouseId), any(LocalDate.class))).thenReturn(true);
        when(subscriptionRepository.findCurrentByTenantIdAndStatus(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.empty());

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> accessService.requireWmsAccess(tenantId, warehouseId));

        assertEquals(ErrorCode.SUBSCRIPTION_REQUIRED, exception.getErrorCode());
        assertDoesNotThrow(() -> accessService.requireActiveContract(tenantId, warehouseId));
    }

    @Test
    void missingActiveContractBlocksWmsBeforeCheckingSubscription() {
        when(contractRepository.existsCurrentDirectActiveContract(
                eq(tenantId), eq(warehouseId), any(LocalDate.class))).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> accessService.requireWmsAccess(tenantId, warehouseId));
        verifyNoInteractions(subscriptionRepository);
    }

    @Test
    void activeWarehouseListUsesDirectCurrentContracts() {
        Warehouse warehouse = mock(Warehouse.class);
        when(contractRepository.findCurrentDirectWarehousesByTenantId(
                eq(tenantId), any(LocalDate.class))).thenReturn(List.of(warehouse));

        List<Warehouse> result = accessService.findActiveContractWarehouses(tenantId);

        assertEquals(List.of(warehouse), result);
        verify(contractRepository).findCurrentDirectWarehousesByTenantId(
                eq(tenantId), any(LocalDate.class));
    }

    @Test
    void nullTenantOrWarehouseCannotObserve() {
        assertFalse(accessService.canObserveWarehouse(null, warehouseId));
        assertFalse(accessService.canObserveWarehouse(tenantId, null));
        verifyNoInteractions(contractRepository, subscriptionRepository);
    }

    @Test
    void activeStaffAssignmentAllowsWarehouseAccess() {
        UUID staffId = UUID.randomUUID();
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE)).thenReturn(true);

        assertDoesNotThrow(() -> accessService.requireActiveStaffAssignment(
                staffId, tenantId, warehouseId));
    }

    @Test
    void missingStaffAssignmentBlocksWarehouseAccess() {
        UUID staffId = UUID.randomUUID();
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE)).thenReturn(false);

        assertThrows(ForbiddenException.class, () -> accessService.requireActiveStaffAssignment(
                staffId, tenantId, warehouseId));
    }

    @Test
    void accessibleContractWarehousesAreFilteredByStaffAssignment() {
        UUID staffId = UUID.randomUUID();
        Warehouse assigned = mock(Warehouse.class);
        Warehouse notAssigned = mock(Warehouse.class);
        UUID assignedId = UUID.randomUUID();
        UUID notAssignedId = UUID.randomUUID();
        when(assigned.getId()).thenReturn(assignedId);
        when(notAssigned.getId()).thenReturn(notAssignedId);
        when(contractRepository.findCurrentDirectWarehousesByTenantId(
                eq(tenantId), any(LocalDate.class))).thenReturn(List.of(assigned, notAssigned));
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, assignedId, AssignmentStatus.ACTIVE)).thenReturn(true);
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, notAssignedId, AssignmentStatus.ACTIVE)).thenReturn(false);

        assertEquals(List.of(assigned), accessService.findAccessibleContractWarehouses(tenantId, staffId));
    }
}
