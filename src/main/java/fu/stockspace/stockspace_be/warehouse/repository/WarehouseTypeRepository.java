package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarehouseTypeRepository extends JpaRepository<WarehouseType, java.util.UUID> {
    @Query("SELECT wt FROM WarehouseType wt WHERE wt.id = ?1 AND wt.isDeleted = false")
    Optional<WarehouseType> findById(java.util.UUID id);

    @Query("SELECT wt FROM WarehouseType wt WHERE wt.isDeleted = false")
    java.util.List<WarehouseType> findAll();

    @Query("SELECT wt FROM WarehouseType wt WHERE wt.isDeleted = false")
    Page<WarehouseType> findAll(Pageable pageable);

    @Query("SELECT COUNT(wt) FROM WarehouseType wt WHERE wt.isDeleted = false")
    long count();

    @Query("SELECT wt FROM WarehouseType wt WHERE wt.name = ?1 AND wt.isDeleted = false")
    Optional<WarehouseType> findByName(String name);

    @Query("SELECT COUNT(wt) > 0 FROM WarehouseType wt WHERE wt.name = ?1 AND wt.isDeleted = false")
    boolean existsByName(String name);

    @Query("SELECT wt FROM WarehouseType wt WHERE wt.isDeleted = false AND (" +
           "LOWER(wt.name) LIKE :keyword " +
           "OR LOWER(wt.description) LIKE :keyword)")
    Page<WarehouseType> search(@Param("keyword") String keyword, Pageable pageable);
}
