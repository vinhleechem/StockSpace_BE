package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WarehouseTypeRepository extends JpaRepository<WarehouseType, Integer> {
    Optional<WarehouseType> findByName(String name);
    boolean existsByName(String name);
}
