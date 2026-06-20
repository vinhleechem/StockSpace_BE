package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface WarehouseLayoutRepository extends JpaRepository<WarehouseLayout, UUID> {

    Optional<WarehouseLayout> findByWarehouseIdAndIsDefaultTrue(UUID warehouseId);

    Optional<WarehouseLayout> findByWarehouseIdAndTenantId(UUID warehouseId, UUID tenantId);

    List<WarehouseLayout> findByWarehouseId(UUID warehouseId);
}
