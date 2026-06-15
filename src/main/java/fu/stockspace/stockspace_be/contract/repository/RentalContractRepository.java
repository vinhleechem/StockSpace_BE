package fu.stockspace.stockspace_be.contract.repository;

import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface RentalContractRepository extends JpaRepository<RentalContract, Long> {

    Optional<RentalContract> findByBookingId(Long bookingId);

    /** Hợp đồng của Tenant */
    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.booking.tenant.id = :tenantId
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByTenantId(@Param("tenantId") Long tenantId, Pageable pageable);

    /** Hợp đồng liên quan đến kho của Owner */
    @Query("""
            SELECT c FROM RentalContract c
            WHERE c.booking.warehouse.owner.id = :ownerId
            ORDER BY c.createdAt DESC
            """)
    Page<RentalContract> findByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);
}
