package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseZoneRepository extends JpaRepository<WarehouseZone, UUID> {

    List<WarehouseZone> findAllByLayoutId(UUID layoutId);
}
