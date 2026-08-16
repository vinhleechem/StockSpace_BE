package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, UUID> {

    Page<InventoryAudit> findByWarehouseIdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);

    Page<InventoryAudit> findByRequestedByIdAndIsDeletedFalse(UUID requestedById, Pageable pageable);

    Page<InventoryAudit> findByStatusAndIsDeletedFalse(AuditStatus status, Pageable pageable);

    Page<InventoryAudit> findByIsDeletedFalse(Pageable pageable);

    @Query("""
            SELECT a FROM InventoryAudit a
            WHERE a.isDeleted = false
              AND (
                (:warehouseId IS NOT NULL AND a.warehouse.id = :warehouseId)
                OR (:warehouseId IS NULL AND (a.warehouse.id IN :warehouseIds OR a.requestedBy.id = :userId))
              )
            """)
    Page<InventoryAudit> findAuditsForTenant(
            @Param("warehouseId") UUID warehouseId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("userId") UUID userId,
            Pageable pageable
    );
}
