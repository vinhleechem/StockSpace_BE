package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Central authorization policy for tenant warehouse access.
 *
 * Contract access and subscription access are deliberately separate: an
 * active Contract grants observation, while WMS mutation requires both an
 * active Contract and a current active Subscription.
 */
@Service
@RequiredArgsConstructor
public class TenantWarehouseAccessService {

    private final RentalContractRepository contractRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public void requireActiveContract(UUID tenantId, UUID warehouseId) {
        if (!canObserveWarehouse(tenantId, warehouseId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public boolean canObserveWarehouse(UUID tenantId, UUID warehouseId) {
        if (tenantId == null || warehouseId == null) {
            return false;
        }
        return contractRepository.existsCurrentDirectActiveContract(
                tenantId, warehouseId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public void requireActiveSubscription(UUID tenantId) {
        if (tenantId == null || subscriptionRepository.findCurrentByTenantIdAndStatus(
                tenantId, SubscriptionStatus.ACTIVE, LocalDate.now()).isEmpty()) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }

    @Transactional(readOnly = true)
    public void requireWmsAccess(UUID tenantId, UUID warehouseId) {
        requireActiveContract(tenantId, warehouseId);
        requireActiveSubscription(tenantId);
    }

    @Transactional(readOnly = true)
    public List<Warehouse> findActiveContractWarehouses(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return contractRepository.findCurrentDirectWarehousesByTenantId(
                tenantId, LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<Warehouse> findAccessibleContractWarehouses(UUID tenantId, UUID staffId) {
        List<Warehouse> warehouses = findActiveContractWarehouses(tenantId);
        if (staffId == null) {
            return warehouses;
        }

        return warehouses.stream()
                .filter(warehouse -> hasActiveStaffAssignment(
                        staffId, tenantId, warehouse.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public void requireActiveStaffAssignment(UUID staffId, UUID tenantId, UUID warehouseId) {
        if (!hasActiveStaffAssignment(staffId, tenantId, warehouseId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasActiveStaffAssignment(UUID staffId, UUID tenantId, UUID warehouseId) {
        return staffId != null
                && tenantId != null
                && warehouseId != null
                && assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                        staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE);
    }
}
