package fu.stockspace.stockspace_be.booking.repository;

import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, UUID> {

    // ==================== Tenant ====================

    Page<BookingRequest> findByTenantId(Long tenantId, Pageable pageable);

    Optional<BookingRequest> findByIdAndTenantId(UUID id, Long tenantId);

    /**
     * Kiểm tra Tenant đã có booking PENDING cho kho này chưa (tránh spam).
     */
    boolean existsByTenantIdAndWarehouseIdAndStatus(Long tenantId, UUID warehouseId, ApprovalStatus status);

    // ==================== Owner ====================

    /**
     * Lấy danh sách booking request đến kho của Owner.
     */
    @Query("""
            SELECT b FROM BookingRequest b
            WHERE b.warehouse.owner.id = :ownerId
            ORDER BY b.createdAt DESC
            """)
    Page<BookingRequest> findByWarehouseOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("""
            SELECT b FROM BookingRequest b
            WHERE b.id = :bookingId
              AND b.warehouse.owner.id = :ownerId
            """)
    Optional<BookingRequest> findByIdAndOwnerId(@Param("bookingId") UUID bookingId,
                                                @Param("ownerId") Long ownerId);

    // ==================== Admin ====================

    Page<BookingRequest> findByStatus(ApprovalStatus status, Pageable pageable);
}
