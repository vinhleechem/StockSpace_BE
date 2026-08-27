package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @Query("""
            select t from StockTransfer t
            where t.tenant.id = :tenantId
              and t.isActive = true
              and t.isDeleted = false
              and (:sourceWarehouseId is null or t.sourceWarehouse.id = :sourceWarehouseId)
              and (:destinationWarehouseId is null or t.destinationWarehouse.id = :destinationWarehouseId)
              and (:status is null or t.status = :status)
              and (
                    :staffId is null
                    or (
                        exists (
                            select sourceAssignment.id from StaffWarehouseAssignment sourceAssignment
                            where sourceAssignment.staff.id = :staffId
                              and sourceAssignment.tenant.id = :tenantId
                              and sourceAssignment.warehouse.id = t.sourceWarehouse.id
                              and sourceAssignment.status = fu.stockspace.stockspace_be.staff.entity.AssignmentStatus.ACTIVE
                              and sourceAssignment.isActive = true
                              and sourceAssignment.isDeleted = false
                        )
                        and exists (
                            select destinationAssignment.id from StaffWarehouseAssignment destinationAssignment
                            where destinationAssignment.staff.id = :staffId
                              and destinationAssignment.tenant.id = :tenantId
                              and destinationAssignment.warehouse.id = t.destinationWarehouse.id
                              and destinationAssignment.status = fu.stockspace.stockspace_be.staff.entity.AssignmentStatus.ACTIVE
                              and destinationAssignment.isActive = true
                              and destinationAssignment.isDeleted = false
                        )
                    )
              )
            order by t.createdAt desc
            """)
    Page<StockTransfer> search(
            @Param("tenantId") UUID tenantId,
            @Param("sourceWarehouseId") UUID sourceWarehouseId,
            @Param("destinationWarehouseId") UUID destinationWarehouseId,
            @Param("status") StockTransferStatus status,
            @Param("staffId") UUID staffId,
            Pageable pageable);

    @Query("""
            select t from StockTransfer t
            where t.tenant.id = :tenantId
              and t.sourceWarehouse.id in :warehouseIds
              and t.destinationWarehouse.id in :warehouseIds
              and t.isActive = true
              and t.isDeleted = false
            order by t.createdAt desc, t.id desc
            """)
    List<StockTransfer> findActiveOperationsForStaff(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseIds") Collection<UUID> warehouseIds);

    @Query("""
            select t from StockTransfer t
            where t.id = :transferId
              and t.tenant.id = :tenantId
              and t.isActive = true
              and t.isDeleted = false
            """)
    Optional<StockTransfer> findByIdAndTenantIdAndIsDeletedFalse(
            @Param("transferId") UUID transferId,
            @Param("tenantId") UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from StockTransfer t where t.id = :id and t.isDeleted = false")
    Optional<StockTransfer> findByIdForUpdate(@Param("id") UUID id);
}
