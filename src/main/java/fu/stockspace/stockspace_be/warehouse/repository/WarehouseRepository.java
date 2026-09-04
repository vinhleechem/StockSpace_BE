package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    @Query("SELECT w FROM Warehouse w WHERE w.id = ?1 AND w.isDeleted = false")
    Optional<Warehouse> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Warehouse w WHERE w.id = :id AND w.isDeleted = false")
    Optional<Warehouse> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT w FROM Warehouse w WHERE w.isDeleted = false")
    java.util.List<Warehouse> findAll();

    @Query("SELECT w FROM Warehouse w WHERE w.isDeleted = false")
    Page<Warehouse> findAll(Pageable pageable);

    @Query("SELECT COUNT(w) FROM Warehouse w WHERE w.isDeleted = false")
    long count();

    @EntityGraph(attributePaths = "type")
    @Query("""
            SELECT w FROM Warehouse w
            WHERE w.id = :id
              AND w.isActive = true
              AND w.isDeleted = false
              AND w.status = fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.AVAILABLE
              AND w.publishedAt IS NOT NULL
              AND w.visibleUntil IS NOT NULL
              AND w.visibleUntil >= CURRENT_TIMESTAMP
            """)
    Optional<Warehouse> findPublicAvailableById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "type")
    @Query("""
            SELECT w FROM Warehouse w
              WHERE w.isActive = true
                AND w.isDeleted = false
                AND ((:status IS NULL AND w.status = fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus.AVAILABLE) OR (:status IS NOT NULL AND w.status = :status))
              AND w.publishedAt IS NOT NULL
              AND w.visibleUntil IS NOT NULL
              AND w.visibleUntil >= CURRENT_TIMESTAMP
              AND (:keyword IS NULL OR LOWER(w.name) LIKE :keyword
                   OR LOWER(w.address) LIKE :keyword
                   OR LOWER(w.provinceName) LIKE :keyword
                   OR LOWER(w.districtName) LIKE :keyword
                   OR LOWER(w.description) LIKE :keyword
                   OR LOWER(w.type.name) LIKE :keyword)
              AND (:provinceCode IS NULL OR w.provinceCode = :provinceCode)
              AND (:districtCode IS NULL OR w.districtCode = :districtCode)
              AND (:warehouseTypeId IS NULL OR w.type.id = :warehouseTypeId)
              AND (:minPrice IS NULL OR (w.rentalPrice IS NOT NULL AND w.rentalPrice >= :minPrice))
              AND (:maxPrice IS NULL OR (w.rentalPrice IS NOT NULL AND w.rentalPrice <= :maxPrice))
              AND (:minCapacity IS NULL OR w.capacity >= :minCapacity)
              AND (:maxCapacity IS NULL OR w.capacity <= :maxCapacity)
              AND (:isVerified IS NULL OR w.isVerified = :isVerified)
            """)
    Page<Warehouse> searchPublic(
            @Param("keyword") String keyword,
            @Param("status") WarehouseStatus status,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("minCapacity") BigDecimal minCapacity,
            @Param("maxCapacity") BigDecimal maxCapacity,
            @Param("provinceCode") String provinceCode,
            @Param("districtCode") String districtCode,
            @Param("warehouseTypeId") UUID warehouseTypeId,
            @Param("isVerified") Boolean isVerified,
            Pageable pageable
    );

    @Query("""
            SELECT w FROM Warehouse w
            WHERE w.isDeleted = false
              AND (:keyword IS NULL OR LOWER(w.name) LIKE :keyword
                   OR LOWER(w.address) LIKE :keyword
                   OR LOWER(w.description) LIKE :keyword
                   OR LOWER(w.type.name) LIKE :keyword)
              AND (:status IS NULL OR w.status = :status)
              AND (:isVerified IS NULL OR w.isVerified = :isVerified)
            """)
    Page<Warehouse> searchAll(
            @Param("keyword") String keyword,
            @Param("status") WarehouseStatus status,
            @Param("isVerified") Boolean isVerified,
            Pageable pageable
    );

    @Query("SELECT w FROM Warehouse w WHERE w.owner.id = ?1 AND w.isDeleted = false")
    Page<Warehouse> findByOwnerId(UUID ownerId, Pageable pageable);

    @Query("SELECT w FROM Warehouse w WHERE w.id = ?1 AND w.owner.id = ?2 AND w.isDeleted = false")
    Optional<Warehouse> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("SELECT COUNT(w) > 0 FROM Warehouse w WHERE w.id = ?1 AND w.owner.id = ?2 AND w.isDeleted = false")
    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("SELECT COUNT(w) > 0 FROM Warehouse w WHERE w.type.id = ?1 AND w.isDeleted = false")
    boolean existsByTypeId(java.util.UUID typeId);

    @Query("""
            SELECT COUNT(c) > 0 FROM RentalContract c
            WHERE c.warehouse.id = :warehouseId
              AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
              AND c.isActive = true
              AND c.isDeleted = false
              AND c.startDate <= CURRENT_DATE
              AND c.endDate >= CURRENT_DATE
            """)
    boolean hasCurrentActiveContract(@Param("warehouseId") UUID warehouseId);

    @Query("""
            SELECT COUNT(o) > 0 FROM ListingOrder o
            WHERE o.warehouse.id = :warehouseId
              AND o.status = fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus.PAID
              AND o.periodEnd IS NOT NULL
              AND o.periodEnd > :now
              AND o.isDeleted = false
            """)
    boolean hasOpenPaidPublication(
            @Param("warehouseId") UUID warehouseId,
            @Param("now") LocalDateTime now
    );
}
