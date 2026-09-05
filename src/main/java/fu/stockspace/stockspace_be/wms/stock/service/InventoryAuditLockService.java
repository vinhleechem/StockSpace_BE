package fu.stockspace.stockspace_be.wms.stock.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditLock;
import fu.stockspace.stockspace_be.wms.stock.repository.InventoryAuditLockRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * First version deliberately locks the warehouse, even for a rack/bin scope.
 * This is conservative but guarantees that an untracked movement cannot make
 * the count stale. The lock can be narrowed after every stock mutation path is
 * routed through a single movement ledger.
 */
@Service
@RequiredArgsConstructor
public class InventoryAuditLockService {
    private final InventoryAuditLockRepository lockRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public InventoryAuditLock acquire(InventoryAudit audit) {
        UUID warehouseId = audit.getWarehouse().getId();
        if (warehouseRepository != null) {
            warehouseRepository.findByIdForUpdate(warehouseId)
                    .orElseThrow(() -> new ResourceConflictException(ErrorCode.WAREHOUSE_NOT_FOUND));
        }
        if (lockRepository.findActiveForUpdate(warehouseId).isPresent()) {
            throw new ResourceConflictException(ErrorCode.AUDIT_MOVEMENT_LOCKED,
                    "Kho đang có một phiếu kiểm kê đang thực hiện");
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
