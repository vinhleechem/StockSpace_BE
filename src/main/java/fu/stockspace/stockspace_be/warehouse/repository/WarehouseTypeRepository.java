package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WarehouseTypeRepository extends JpaRepository<WarehouseType, Integer> {
    Optional<WarehouseType> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT wt FROM WarehouseType wt WHERE " +
           "LOWER(wt.name) LIKE :keyword " +
           "OR LOWER(wt.description) LIKE :keyword")
    Page<WarehouseType> search(@Param("keyword") String keyword, Pageable pageable);
}
