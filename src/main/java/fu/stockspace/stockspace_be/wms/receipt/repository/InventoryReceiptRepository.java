package fu.stockspace.stockspace_be.wms.receipt.repository;

import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReceiptRepository extends JpaRepository<InventoryReceipt, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReceipt r where r.id = :id")
    Optional<InventoryReceipt> findByIdForUpdate(@Param("id") UUID id);

    Page<InventoryReceipt> findByWarehouseIdAndTypeAndIsDeletedFalse(UUID warehouseId, DocumentType type, Pageable pageable);
    Page<InventoryReceipt> findByWarehouseIdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);
    Page<InventoryReceipt> findByWarehouseIdAndTypeAndStatusAndIsDeletedFalse(UUID warehouseId, DocumentType type, ApprovalStatus status, Pageable pageable);
    Page<InventoryReceipt> findByWarehouseIdAndStatusAndIsDeletedFalse(UUID warehouseId, ApprovalStatus status, Pageable pageable);

    @Query("""
            select r from InventoryReceipt r
            where r.warehouse.id in :warehouseIds
              and r.isActive = true
              and r.isDeleted = false
            order by r.createdAt desc, r.id desc
            """)
    List<InventoryReceipt> findActiveOperationsByWarehouseIds(
            @Param("warehouseIds") Collection<UUID> warehouseIds);
}

