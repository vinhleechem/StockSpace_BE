package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRackRepository extends JpaRepository<WarehouseRack, UUID> {

    List<WarehouseRack> findAllByLayoutId(UUID layoutId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WarehouseRack r where r.id = :id")
    Optional<WarehouseRack> findByIdForUpdate(@Param("id") UUID id);
}
