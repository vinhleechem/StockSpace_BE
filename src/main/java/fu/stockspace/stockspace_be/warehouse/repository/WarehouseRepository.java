package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho Warehouse.
 *
 * searchWarehouses — public search: filter theo keyword, status, price, capacity
 * findByOwnerId    — owner xem kho của mình
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    // ==================== Public Search ====================

    /**
     * Tìm kiếm kho công khai: chỉ trả về kho đã được verify + AVAILABLE
     * Filter: keyword (name/address), pricePerMonth, capacity
     */
    @Query("""
            SELECT w FROM Warehouse w
            WHERE w.isVerified = true
              AND (:status IS NULL OR w.status = :status)
              AND (:keyword IS NULL OR LOWER(w.name) LIKE :keyword
                   OR LOWER(w.address) LIKE :keyword)
              AND (:minPrice IS NULL OR w.pricePerMonth >= :minPrice)
              AND (:maxPrice IS NULL OR w.pricePerMonth <= :maxPrice)
              AND (:minCapacity IS NULL OR w.capacity >= :minCapacity)
            """)
    Page<Warehouse> searchPublic(
            @Param("keyword") String keyword,
            @Param("status") WarehouseStatus status,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minCapacity") BigDecimal minCapacity,
            Pageable pageable
    );

    // ==================== Admin Search (all warehouses) ====================

    @Query("""
            SELECT w FROM Warehouse w
            WHERE (:keyword IS NULL OR LOWER(w.name) LIKE :keyword
                   OR LOWER(w.address) LIKE :keyword)
              AND (:status IS NULL OR w.status = :status)
              AND (:isVerified IS NULL OR w.isVerified = :isVerified)
            """)
    Page<Warehouse> searchAll(
            @Param("keyword") String keyword,
            @Param("status") WarehouseStatus status,
            @Param("isVerified") Boolean isVerified,
            Pageable pageable
    );

    // ==================== Owner ====================

    Page<Warehouse> findByOwnerId(Long ownerId, Pageable pageable);

    Optional<Warehouse> findByIdAndOwnerId(UUID id, Long ownerId);

    // ==================== Misc ====================

    boolean existsByIdAndOwnerId(UUID id, Long ownerId);

    boolean existsByTypeId(Integer typeId);
}
