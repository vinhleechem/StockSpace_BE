package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, UUID> {

    List<WarehouseBin> findAllByRackId(UUID rackId);

    List<WarehouseBin> findAllByRackZoneLayoutId(UUID layoutId);

    Optional<WarehouseBin> findByCode(String code);
}
