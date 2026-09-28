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
            if (existingAuditId != null && existingAuditId.equals(audit.getId())) {
                return existingLock;
            }
            throw new ResourceConflictException(ErrorCode.AUDIT_MOVEMENT_LOCKED,
                    "Kho đang có một phiếu kiểm kê đang thực hiện");
        }
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
