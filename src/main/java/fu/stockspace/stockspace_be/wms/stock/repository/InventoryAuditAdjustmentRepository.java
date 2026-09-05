package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InventoryAuditAdjustmentRepository extends JpaRepository<InventoryAuditAdjustment, UUID> {
    boolean existsByAuditItemId(UUID auditItemId);

    boolean existsByAuditItemIdAndBatchId(UUID auditItemId, UUID batchId);
}
