package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

    List<StockBatch> findByBinId(UUID binId);

    List<StockBatch> findByRackId(UUID rackId);

    List<StockBatch> findByZoneId(UUID zoneId);

    boolean existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(UUID binId, int quantity);

    boolean existsByRackIdAndQuantityGreaterThanAndIsDeletedFalse(UUID rackId, int quantity);

    boolean existsByZoneIdAndQuantityGreaterThanAndIsDeletedFalse(UUID zoneId, int quantity);

    boolean existsBySkuIdAndIsDeletedFalse(UUID skuId);
}
