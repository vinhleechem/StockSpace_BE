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

    Page<BookingRequest> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<BookingRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Kiểm tra Tenant đã có booking PENDING cho kho này chưa (tránh spam).
     */
    boolean existsByTenantIdAndWarehouseIdAndStatus(UUID tenantId, UUID warehouseId, ApprovalStatus status);

    @Query("SELECT COUNT(b) > 0 FROM BookingRequest b WHERE b.warehouse.id = :warehouseId AND b.status IN :statuses")
    boolean existsByWarehouseIdAndStatusIn(@Param("warehouseId") UUID warehouseId, @Param("statuses") java.util.List<ApprovalStatus> statuses);

    // ==================== Owner ====================

    /**
     * Lấy danh sách booking request đến kho của Owner.
     */
    @Query("""
            SELECT b FROM BookingRequest b
            WHERE b.warehouse.owner.id = :ownerId
            ORDER BY b.createdAt DESC
            """)
    Page<BookingRequest> findByWarehouseOwnerId(@Param("ownerId") UUID ownerId, Pageable pageable);

    @Query("""
            SELECT b FROM BookingRequest b
            WHERE b.id = :bookingId
              AND b.warehouse.owner.id = :ownerId
            """)
    Optional<BookingRequest> findByIdAndOwnerId(@Param("bookingId") UUID bookingId,
                                                @Param("ownerId") UUID ownerId);

    // ==================== Admin ====================

    Page<BookingRequest> findByStatus(ApprovalStatus status, Pageable pageable);
}
