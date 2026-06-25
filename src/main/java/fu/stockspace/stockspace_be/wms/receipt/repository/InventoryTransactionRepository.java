package fu.stockspace.stockspace_be.wms.receipt.repository;

import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, UUID> {
}
