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


/**
 * Repository cho Warehouse.
 *
 * searchWarehouses — public search: filter theo keyword, status, price, capacity
 * findByOwnerId    — owner xem kho của mình
 */
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    @Query("SELECT w FROM Warehouse w WHERE w.id = ?1 AND w.isDeleted = false")
    Optional<Warehouse> findById(Long id);

    @Query("SELECT w FROM Warehouse w WHERE w.isDeleted = false")
    java.util.List<Warehouse> findAll();

    @Query("SELECT w FROM Warehouse w WHERE w.isDeleted = false")
    Page<Warehouse> findAll(Pageable pageable);

    @Query("SELECT COUNT(w) FROM Warehouse w WHERE w.isDeleted = false")
    long count();

    // ==================== Public Search ====================

    /**
     * Tìm kiếm kho công khai: trả về kho đã được duyệt đăng bài (không cần đã kiểm định)
     * Filter: keyword (name/address), pricePerMonth, capacity
     */
    @Query("""
            SELECT w FROM Warehouse w
            WHERE w.status <> fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.PENDING_APPROVAL
              AND w.isDeleted = false
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
            WHERE w.isDeleted = false
              AND (:keyword IS NULL OR LOWER(w.name) LIKE :keyword
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

    @Query("SELECT w FROM Warehouse w WHERE w.owner.id = ?1 AND w.isDeleted = false")
    Page<Warehouse> findByOwnerId(Long ownerId, Pageable pageable);

    @Query("SELECT w FROM Warehouse w WHERE w.id = ?1 AND w.owner.id = ?2 AND w.isDeleted = false")
    Optional<Warehouse> findByIdAndOwnerId(Long id, Long ownerId);

    // ==================== Misc ====================

    @Query("SELECT COUNT(w) > 0 FROM Warehouse w WHERE w.id = ?1 AND w.owner.id = ?2 AND w.isDeleted = false")
    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    @Query("SELECT COUNT(w) > 0 FROM Warehouse w WHERE w.type.id = ?1 AND w.isDeleted = false")
    boolean existsByTypeId(Integer typeId);
}
