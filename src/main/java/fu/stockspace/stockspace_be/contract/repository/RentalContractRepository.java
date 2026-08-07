package fu.stockspace.stockspace_be.contract.repository;

import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


import java.util.UUID;

public interface RentalContractRepository extends JpaRepository<RentalContract, UUID> {

    Optional<RentalContract> findByBookingId(UUID bookingId);

    /** Hợp đồng của Tenant */
    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.booking.tenant.id = :tenantId
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /** Hợp đồng liên quan đến kho của Owner */
    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.booking.warehouse.owner.id = :ownerId
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("SELECT c FROM RentalContract c WHERE c.status = :status AND c.submittedAt < :dateTime")
    java.util.List<RentalContract> findByStatusAndSubmittedAtBefore(@Param("status") fu.stockspace.stockspace_be.contract.entity.ContractStatus status, @Param("dateTime") java.time.LocalDateTime dateTime);

    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            WHERE c.booking.tenant.id = :tenantId
              AND c.booking.warehouse.id = :warehouseId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.booking.isActive = true
              AND c.booking.isDeleted = false
            """)
    boolean existsByTenantIdAndWarehouseIdAndStatusActive(@Param("tenantId") UUID tenantId, @Param("warehouseId") UUID warehouseId);
}
