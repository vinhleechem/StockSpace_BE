package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditLock;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditLockRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * First version deliberately locks the warehouse, even for a rack/bin scope.
 * This is conservative but guarantees that an untracked movement cannot make
 * the count stale. The lock can be narrowed after every stock mutation path is
 * routed through a single movement ledger.
 *
 * The lock is held after start for every unresolved workflow state:
 * {@code IN_PROGRESS}, {@code SUBMITTED}, {@code EDIT_REQUESTED},
 * {@code REOPENED}, and {@code RECOUNT_REQUIRED}. A recount is still part of
 * the unresolved audit; allowing stock movements before the next count would
 * make the recount impossible to reconcile with the submitted result.
 */
@Service
@RequiredArgsConstructor
public class InventoryAuditLockService {
    private final InventoryAuditLockRepository lockRepository;
    private final InventoryAuditRepository auditRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public InventoryAuditLock acquire(InventoryAudit audit) {
        UUID warehouseId = audit.getWarehouse().getId();
        if (warehouseRepository != null) {
            warehouseRepository.findByIdForUpdate(warehouseId)
                    .orElseThrow(() -> new ResourceConflictException(ErrorCode.WAREHOUSE_NOT_FOUND));
        }
        Optional<InventoryAuditLock> activeLock = lockRepository.findActiveForUpdate(warehouseId);
        if (activeLock.isPresent()) {
            InventoryAuditLock existingLock = activeLock.get();
            UUID existingAuditId = existingLock.getAudit() == null
                    ? null : existingLock.getAudit().getId();
            // Starting the next recount round must reuse the lock that this
            // audit already owns instead of treating it as a competing audit.
            if (existingAuditId != null && existingAuditId.equals(audit.getId())) {
                return existingLock;
            }
            throw new ResourceConflictException(ErrorCode.AUDIT_MOVEMENT_LOCKED,
                    "Kho đang có một phiếu kiểm kê đang thực hiện");
        }
        // Keep this reservation check as a defensive guard for legacy data where
        // a RECOUNT_REQUIRED audit has no lock. Normally the active lock check
        // above is what prevents another audit from starting.
        if (audit.getStatus() != AuditStatus.RECOUNT_REQUIRED
                && auditRepository.existsByWarehouseIdAndStatusAndIsActiveTrueAndIsDeletedFalse(
                        warehouseId, AuditStatus.RECOUNT_REQUIRED)) {
            throw new ResourceConflictException(ErrorCode.AUDIT_RECOUNT_RESERVED);
        }
        try {
            return lockRepository.saveAndFlush(InventoryAuditLock.builder()
                    .audit(audit)
                    .warehouse(audit.getWarehouse())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new ResourceConflictException(ErrorCode.AUDIT_MOVEMENT_LOCKED,
                    "Kho vừa được khóa bởi một phiếu kiểm kê khác");
        }
    }

    @Transactional
    public void release(UUID auditId) {
        lockRepository.findActiveByAuditId(auditId).ifPresent(lock -> {
            lock.setReleasedAt(LocalDateTime.now());
            lock.setActive(false);
            lockRepository.save(lock);
        });
    }

    // This method takes a row lock to serialize the check with audit start. It
    // must therefore run in a write-capable transaction on PostgreSQL.
    @Transactional
    public void assertMovementAllowed(UUID warehouseId) {
        if (warehouseRepository != null) {
            warehouseRepository.findByIdForUpdate(warehouseId)
                    .orElseThrow(() -> new ResourceConflictException(ErrorCode.WAREHOUSE_NOT_FOUND));
        }
        if (lockRepository.findActive(warehouseId).isPresent()) {
            throw new ResourceConflictException(ErrorCode.AUDIT_MOVEMENT_LOCKED);
        }
    }

    @Transactional(readOnly = true)
    public boolean isLockedBy(UUID auditId, UUID warehouseId) {
        return lockRepository.findActiveByAuditId(auditId)
                .map(lock -> lock.getWarehouse().getId().equals(warehouseId))
                .orElse(false);
    }
}
