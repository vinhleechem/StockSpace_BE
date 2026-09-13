package fu.stockspace.stockspace_be.wms.receipt.repository;

import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceiptItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryReceiptItemRepository extends JpaRepository<InventoryReceiptItem, UUID> {
    List<InventoryReceiptItem> findByReceiptId(UUID receiptId);

    @Query("""
            select distinct i from InventoryReceiptItem i
            left join fetch i.sku sku
            left join fetch sku.uom
            left join fetch i.rack
            left join fetch i.bin
            left join fetch i.stockBatch
            where i.receipt.id in :receiptIds
            order by i.receipt.id, i.id
            """)
    List<InventoryReceiptItem> findByReceiptIdInWithDetails(
            @Param("receiptIds") Collection<UUID> receiptIds);
}
