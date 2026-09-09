package fu.stockspace.stockspace_be.contract.repository;

import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;

public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    @Query("""
            SELECT c.status, COUNT(c) FROM RentalContract c
            WHERE c.owner IS NOT NULL
              AND c.tenant IS NOT NULL
              AND c.warehouse IS NOT NULL
              AND c.isDeleted = false
            GROUP BY c.status
            """)
    List<Object[]> countDirectContractsByStatus();

    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.tenant.id = :tenantId
              AND c.status <> fu.stockspace.stockspace_be.contract.entity.ContractStatus.DRAFT
              AND c.isDeleted = false
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);


    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.owner.id = :ownerId
              AND c.isDeleted = false
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM RentalContract c WHERE c.id = :contractId")
    Optional<RentalContract> findByIdForUpdate(@Param("contractId") UUID contractId);

    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.renewedFromContract.id = :sourceContractId
              AND c.status IN (
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.DRAFT,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.PENDING_TENANT_CONFIRM,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.CHANGES_REQUESTED,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.SCHEDULED,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.EXPIRED)
              AND c.isActive = true
              AND c.isDeleted = false
            ORDER BY c.createdAt ASC
            """)
    List<RentalContract> findBlockingRenewalsBySourceId(
            @Param("sourceContractId") UUID sourceContractId);

    @Query("SELECT c FROM RentalContract c WHERE c.status = :status AND c.submittedAt < :dateTime")
    java.util.List<RentalContract> findByStatusAndSubmittedAtBefore(@Param("status") fu.stockspace.stockspace_be.contract.entity.ContractStatus status, @Param("dateTime") java.time.LocalDateTime dateTime);

    @Query("""
            SELECT c.id FROM RentalContract c
            WHERE c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.endDate >= :fromDate
              AND c.endDate <= :toDate
              AND c.expiryReminderSent = false
            """)
    List<UUID> findActiveContractIdsEndingBetween(
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);

    @Query("""
            SELECT c.id FROM RentalContract c
            WHERE c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.SCHEDULED
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= :today
            """)
    List<UUID> findScheduledContractIdsDueOnOrBefore(
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT c.id FROM RentalContract c
            WHERE c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.endDate < :today
            """)
    List<UUID> findActiveContractIdsEndingBefore(
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            WHERE c.tenant.id = :tenantId
              AND c.warehouse.id = :warehouseId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= CURRENT_DATE
              AND c.endDate >= CURRENT_DATE
            """)
    boolean existsByTenantIdAndWarehouseIdAndStatusActive(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId);

    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            WHERE c.tenant.id = :tenantId
              AND c.warehouse.id = :warehouseId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate IS NOT NULL
              AND c.endDate IS NOT NULL
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    boolean existsCurrentDirectActiveContract(
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("today") java.time.LocalDate today);

    /**
     * Direct-contract overlap check used while the parent warehouse row is
     * locked. Date boundaries are inclusive: a contract ending on a date
     * conflicts with another contract starting on that same date.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            WHERE c.id <> :contractId
              AND c.tenant.id = :tenantId
              AND c.warehouse.id = :warehouseId
              AND c.status IN (
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.PENDING_TENANT_CONFIRM,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.SCHEDULED,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE)
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate IS NOT NULL
              AND c.endDate IS NOT NULL
              AND c.startDate <= :endDate
              AND c.endDate >= :startDate
            """)
    boolean existsDirectDateOverlapForSubmit(
            @Param("contractId") UUID contractId,
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);

    @Query("""
            SELECT COALESCE(SUM(c.leasedAreaM2), 0)
            FROM RentalContract c
            WHERE c.warehouse.id = :warehouseId
              AND (:excludedContractId IS NULL OR c.id <> :excludedContractId)
              AND c.status IN (
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.PENDING_TENANT_CONFIRM,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.SCHEDULED,
                    fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE)
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate IS NOT NULL
              AND c.endDate IS NOT NULL
              AND c.startDate <= :endDate
              AND c.endDate >= :startDate
            """)
    BigDecimal sumReservedAreaForDateRange(
            @Param("warehouseId") UUID warehouseId,
            @Param("excludedContractId") UUID excludedContractId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate);

    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            WHERE c.id <> :contractId
              AND c.tenant.id = :tenantId
              AND c.warehouse.id = :warehouseId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    boolean existsOtherCurrentDirectActiveContract(
            @Param("contractId") UUID contractId,
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT DISTINCT w FROM RentalContract c
            JOIN c.warehouse w
            WHERE c.tenant.id = :tenantId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate IS NOT NULL
              AND c.endDate IS NOT NULL
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    List<fu.stockspace.stockspace_be.warehouse.entity.Warehouse> findCurrentDirectWarehousesByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT COUNT(c) FROM RentalContract c
            WHERE c.owner.id = :ownerId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    long countCurrentDirectActiveContractsByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT COUNT(DISTINCT c.tenant.id) FROM RentalContract c
            WHERE c.owner.id = :ownerId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    long countDistinctCurrentDirectActiveTenantsByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT DISTINCT c.warehouse.id FROM RentalContract c
            WHERE c.owner.id = :ownerId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    List<UUID> findCurrentDirectActiveWarehouseIdsByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT COUNT(c) FROM RentalContract c
            WHERE c.tenant.id = :tenantId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate IS NOT NULL
              AND c.endDate IS NOT NULL
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    long countCurrentDirectActiveContractsByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT COUNT(DISTINCT c.warehouse.id) FROM RentalContract c
            WHERE c.tenant.id = :tenantId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate IS NOT NULL
              AND c.endDate IS NOT NULL
              AND c.startDate <= :today
              AND c.endDate >= :today
            """)
    long countCurrentDirectActiveWarehousesByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("today") java.time.LocalDate today);

    long countByTenantIdAndStatusAndIsActiveTrueAndIsDeletedFalse(
            UUID tenantId, ContractStatus status);

}

