package fu.stockspace.stockspace_be.wms.receipt.repository;

import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryReceiptItemRepository extends JpaRepository<InventoryReceiptItem, UUID> {
    List<InventoryReceiptItem> findByReceiptId(UUID receiptId);
}
