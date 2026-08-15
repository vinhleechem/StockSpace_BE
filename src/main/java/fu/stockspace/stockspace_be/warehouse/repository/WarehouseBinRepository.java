package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
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
public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, UUID> {

    @Query("select b from WarehouseBin b where b.rack.id = :rackId and b.isDeleted = false")
    List<WarehouseBin> findAllByRackId(@Param("rackId") UUID rackId);

    @Query("select b from WarehouseBin b where b.rack.layout.id = :layoutId and b.isDeleted = false")
    List<WarehouseBin> findAllByRackLayoutId(@Param("layoutId") UUID layoutId);

    Optional<WarehouseBin> findByIdAndIsDeletedFalse(UUID id);

    Optional<WarehouseBin> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WarehouseBin b where b.id = :id and b.isDeleted = false")
    Optional<WarehouseBin> findByIdForUpdate(@Param("id") UUID id);
}
