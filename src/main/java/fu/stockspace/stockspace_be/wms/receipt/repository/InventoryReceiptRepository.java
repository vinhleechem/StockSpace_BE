package fu.stockspace.stockspace_be.wms.receipt.repository;

import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InventoryReceiptRepository extends JpaRepository<InventoryReceipt, UUID> {
    Page<InventoryReceipt> findByWarehouseIdAndTypeAndIsDeletedFalse(UUID warehouseId, DocumentType type, Pageable pageable);
    Page<InventoryReceipt> findByWarehouseIdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);
    Page<InventoryReceipt> findByWarehouseIdAndTypeAndStatusAndIsDeletedFalse(UUID warehouseId, DocumentType type, ApprovalStatus status, Pageable pageable);
    Page<InventoryReceipt> findByWarehouseIdAndStatusAndIsDeletedFalse(UUID warehouseId, ApprovalStatus status, Pageable pageable);
}

