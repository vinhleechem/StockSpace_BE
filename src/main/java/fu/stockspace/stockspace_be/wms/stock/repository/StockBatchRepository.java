package fu.stockspace.stockspace_be.wms.stock.repository;

import fu.stockspace.stockspace_be.wms.stock.entity.StockBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, UUID> {

    List<StockBatch> findByBinId(UUID binId);

    List<StockBatch> findByRackId(UUID rackId);

    boolean existsByBinIdAndQuantityGreaterThanAndIsDeletedFalse(UUID binId, int quantity);

    boolean existsByRackIdAndQuantityGreaterThanAndIsDeletedFalse(UUID rackId, int quantity);

    boolean existsBySkuIdAndIsDeletedFalse(UUID skuId);

    Optional<StockBatch> findBySkuIdAndWarehouseIdAndRackIdAndBinIdAndIsDeletedFalse(
            UUID skuId, UUID warehouseId, UUID rackId, UUID binId);

    // Dev B — Module 5
    Page<StockBatch> findByWarehouseIdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);

    List<StockBatch> findBySkuIdAndIsDeletedFalse(UUID skuId);

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM StockBatch b WHERE b.skuId = :skuId AND b.isDeleted = false")
    int sumQuantityBySkuId(@Param("skuId") UUID skuId);

    Optional<StockBatch> findByIdAndIsDeletedFalse(UUID id);
}
