package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WarehouseRackRepository extends JpaRepository<WarehouseRack, UUID> {

    List<WarehouseRack> findAllByLayoutId(UUID layoutId);
}
