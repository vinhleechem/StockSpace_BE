package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


import java.util.UUID;

public interface WarehouseImageRepository extends JpaRepository<WarehouseImage, UUID> {

    List<WarehouseImage> findAllByWarehouseIdOrderByDisplayOrderAsc(UUID warehouseId);

    @Modifying
    @Query("DELETE FROM WarehouseImage wi WHERE wi.warehouse.id = :warehouseId")
    void deleteAllByWarehouseId(@Param("warehouseId") UUID warehouseId);

    int countByWarehouseId(UUID warehouseId);
}
