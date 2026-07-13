package fu.stockspace.stockspace_be.wms.receipt.repository;

import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {

    Page<InventoryTransaction> findByBatchId(UUID batchId, Pageable pageable);

    List<InventoryTransaction> findByReceiptId(UUID receiptId);
}

