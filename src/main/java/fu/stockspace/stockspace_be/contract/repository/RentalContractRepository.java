package fu.stockspace.stockspace_be.contract.repository;

import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    Optional<RentalContract> findByBookingId(UUID bookingId);


    @Query("""
            SELECT c FROM RentalContract c
            LEFT JOIN c.tenant directTenant
            LEFT JOIN c.booking legacyBooking
            LEFT JOIN legacyBooking.tenant legacyTenant
            WHERE directTenant.id = :tenantId
               OR legacyTenant.id = :tenantId
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);


    @Query("""
            SELECT c FROM RentalContract c
            LEFT JOIN c.owner directOwner
            LEFT JOIN c.booking legacyBooking
            LEFT JOIN legacyBooking.warehouse legacyWarehouse
            LEFT JOIN legacyWarehouse.owner legacyOwner
            WHERE directOwner.id = :ownerId
               OR legacyOwner.id = :ownerId
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("SELECT c FROM RentalContract c WHERE c.status = :status AND c.submittedAt < :dateTime")
    java.util.List<RentalContract> findByStatusAndSubmittedAtBefore(@Param("status") fu.stockspace.stockspace_be.contract.entity.ContractStatus status, @Param("dateTime") java.time.LocalDateTime dateTime);

    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.endDate >= :fromDate
              AND c.endDate <= :toDate
              AND c.expiryReminderSent = false
            """)
    java.util.List<RentalContract> findActiveContractsEndingBetween(
            @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate);

    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.endDate < :today
            """)
    java.util.List<RentalContract> findActiveContractsEndingBefore(
            @Param("today") java.time.LocalDate today);

    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            LEFT JOIN c.tenant directTenant
            LEFT JOIN c.warehouse directWarehouse
            LEFT JOIN c.booking legacyBooking
            LEFT JOIN legacyBooking.tenant legacyTenant
            LEFT JOIN legacyBooking.warehouse legacyWarehouse
            WHERE (directTenant.id = :tenantId OR legacyTenant.id = :tenantId)
              AND (directWarehouse.id = :warehouseId OR legacyWarehouse.id = :warehouseId)
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND (legacyBooking IS NULL OR (legacyBooking.isActive = true AND legacyBooking.isDeleted = false))
            """)
    boolean existsByTenantIdAndWarehouseIdAndStatusActive(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId);

    @Query("""
            SELECT DISTINCT w FROM RentalContract c
            JOIN c.warehouse w
            WHERE c.tenant.id = :tenantId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
            """)
    List<fu.stockspace.stockspace_be.warehouse.entity.Warehouse> findActiveDirectWarehousesByTenantId(@Param("tenantId") UUID tenantId);

    @Query("""
            SELECT DISTINCT legacyBooking.warehouse FROM RentalContract c
            JOIN c.booking legacyBooking
            WHERE legacyBooking.tenant.id = :tenantId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND legacyBooking.isActive = true
              AND legacyBooking.isDeleted = false
            """)
    List<fu.stockspace.stockspace_be.warehouse.entity.Warehouse> findActiveLegacyWarehousesByTenantId(@Param("tenantId") UUID tenantId);

    default List<fu.stockspace.stockspace_be.warehouse.entity.Warehouse> findActiveRentedWarehousesByTenantId(UUID tenantId) {
        List<fu.stockspace.stockspace_be.warehouse.entity.Warehouse> warehouses =
                new ArrayList<>(findActiveDirectWarehousesByTenantId(tenantId));
        findActiveLegacyWarehousesByTenantId(tenantId).forEach(legacyWarehouse -> {
            if (warehouses.stream().noneMatch(warehouse -> warehouse.getId().equals(legacyWarehouse.getId()))) {
                warehouses.add(legacyWarehouse);
            }
        });
        return warehouses;
    }
}

