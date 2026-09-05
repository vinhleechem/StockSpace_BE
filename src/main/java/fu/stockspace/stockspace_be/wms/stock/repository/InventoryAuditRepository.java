package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.InventoryAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

@Repository
public interface InventoryAuditRepository extends JpaRepository<InventoryAudit, UUID> {

    Page<InventoryAudit> findByWarehouseIdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);

    Page<InventoryAudit> findByRequestedByIdAndIsDeletedFalse(UUID requestedById, Pageable pageable);

    Page<InventoryAudit> findByStatusAndIsDeletedFalse(AuditStatus status, Pageable pageable);

    Page<InventoryAudit> findByIsDeletedFalse(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from InventoryAudit a
            where a.id = :auditId
              and a.isDeleted = false
            """)
    Optional<InventoryAudit> findByIdForUpdate(@Param("auditId") UUID auditId);

    @Query("""
            select a from InventoryAudit a
            where a.warehouse.id in :warehouseIds
              and a.isActive = true
              and a.isDeleted = false
            order by a.createdAt desc, a.id desc
            """)
    List<InventoryAudit> findActiveOperationsByWarehouseIds(
            @Param("warehouseIds") Collection<UUID> warehouseIds);

    @Query("""
            SELECT COUNT(a) FROM InventoryAudit a
            WHERE a.status IN :statuses
              AND a.isActive = true
              AND a.isDeleted = false
              AND (
                    a.requestedBy.id = :tenantId
                    OR EXISTS (
                        SELECT m.id
                        FROM TenantMember m
                        WHERE m.user.id = a.requestedBy.id
                          AND m.tenant.id = :tenantId
                    )
              )
              AND EXISTS (
                    SELECT c.id
                    FROM RentalContract c
                    WHERE c.tenant.id = :tenantId
                      AND c.warehouse.id = a.warehouse.id
                      AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
                      AND c.isActive = true
                      AND c.isDeleted = false
                      AND c.startDate IS NOT NULL
                      AND c.endDate IS NOT NULL
                      AND c.startDate <= :today
                      AND c.endDate >= :today
              )
            """)
    long countPendingForTenant(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<AuditStatus> statuses,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT a FROM InventoryAudit a
            WHERE a.isDeleted = false
              AND (
                (:warehouseId IS NOT NULL AND a.warehouse.id = :warehouseId)
                OR (:warehouseId IS NULL AND a.warehouse.id IN :warehouseIds)
              )
              AND (
                a.requestedBy.id = :tenantId
                OR EXISTS (
                    SELECT m.id FROM TenantMember m
                    WHERE m.user.id = a.requestedBy.id
                      AND m.tenant.id = :tenantId
                )
              )
            """)
    Page<InventoryAudit> findAuditsForTenant(
            @Param("warehouseId") UUID warehouseId,
            @Param("warehouseIds") Collection<UUID> warehouseIds,
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );
}
