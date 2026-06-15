package fu.stockspace.stockspace_be.warehouse.repository;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface WarehouseImageRepository extends JpaRepository<WarehouseImage, Long> {

    List<WarehouseImage> findAllByWarehouseIdOrderByDisplayOrderAsc(Long warehouseId);

    @Modifying
    @Query("DELETE FROM WarehouseImage wi WHERE wi.warehouse.id = :warehouseId")
    void deleteAllByWarehouseId(@Param("warehouseId") Long warehouseId);

    int countByWarehouseId(Long warehouseId);
}
