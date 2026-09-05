package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryAuditLockRepository extends JpaRepository<InventoryAuditLock, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from InventoryAuditLock l where l.warehouse.id = :warehouseId and l.isActive = true and l.isDeleted = false and l.releasedAt is null")
    Optional<InventoryAuditLock> findActiveForUpdate(@Param("warehouseId") UUID warehouseId);

    @Query("select l from InventoryAuditLock l where l.warehouse.id = :warehouseId and l.isActive = true and l.isDeleted = false and l.releasedAt is null")
    Optional<InventoryAuditLock> findActive(@Param("warehouseId") UUID warehouseId);

    @Query("select l from InventoryAuditLock l where l.audit.id = :auditId and l.isActive = true and l.isDeleted = false and l.releasedAt is null")
    Optional<InventoryAuditLock> findActiveByAuditId(@Param("auditId") UUID auditId);
}
