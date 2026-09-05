package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAuditItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryAuditItemRepository extends JpaRepository<InventoryAuditItem, UUID> {

    List<InventoryAuditItem> findByAuditId(UUID auditId);

    List<InventoryAuditItem> findByAuditIdAndCountRoundOrderById(UUID auditId, int countRound);

    List<InventoryAuditItem> findByBatchId(UUID batchId);
}
