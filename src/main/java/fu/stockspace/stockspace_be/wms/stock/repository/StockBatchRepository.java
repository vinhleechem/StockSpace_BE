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


    Page<StockBatch> findByWarehouseIdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);

    List<StockBatch> findAllByWarehouseIdAndIsDeletedFalse(UUID warehouseId);

    @Query("""
            SELECT b FROM StockBatch b
            JOIN ProductSku s ON s.id = b.skuId
            WHERE b.warehouse.id = :warehouseId
              AND s.tenant.id = :tenantId
              AND b.isActive = true
              AND s.isActive = true
              AND b.isDeleted = false
              AND s.isDeleted = false
            """)
    Page<StockBatch> findByWarehouseIdAndTenantId(
            @Param("warehouseId") UUID warehouseId,
            @Param("tenantId") UUID tenantId,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(DISTINCT b.skuId) AS productCount,
                   COUNT(b.id) AS batchCount,
                   COALESCE(SUM(b.quantity), 0) AS totalQuantity
            FROM StockBatch b
            JOIN ProductSku s ON s.id = b.skuId
            WHERE b.warehouse.id = :warehouseId
              AND s.tenant.id = :tenantId
              AND b.isActive = true
              AND s.isActive = true
              AND b.isDeleted = false
              AND s.isDeleted = false
            """)
    WarehouseStockSummaryProjection summarizeByWarehouseIdAndTenantId(
            @Param("warehouseId") UUID warehouseId,
            @Param("tenantId") UUID tenantId
    );

    List<StockBatch> findBySkuIdAndIsDeletedFalse(UUID skuId);

    @Query("""
            SELECT b FROM StockBatch b
            WHERE b.skuId = :skuId
              AND b.isActive = true
              AND b.isDeleted = false
              AND EXISTS (
                  SELECT c.id FROM RentalContract c
                  WHERE c.tenant.id = :tenantId
                    AND c.warehouse.id = b.warehouse.id
                    AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
                    AND c.isActive = true
                    AND c.isDeleted = false
                    AND c.startDate <= CURRENT_DATE
                    AND c.endDate >= CURRENT_DATE
              )
            """)
    List<StockBatch> findBySkuIdInActiveTenantWarehouses(
            @Param("skuId") UUID skuId,
            @Param("tenantId") UUID tenantId
    );

    @Query("""
            SELECT b FROM StockBatch b
            WHERE b.skuId = :skuId
              AND b.isActive = true
              AND b.isDeleted = false
              AND EXISTS (
                  SELECT c.id FROM RentalContract c
                  WHERE c.tenant.id = :tenantId
                    AND c.warehouse.id = b.warehouse.id
                    AND c.status = fu.stockspace.stockspace_be.contract.entity.ContractStatus.ACTIVE
                    AND c.isActive = true
                    AND c.isDeleted = false
                    AND c.startDate <= CURRENT_DATE
                    AND c.endDate >= CURRENT_DATE
              )
              AND EXISTS (
                  SELECT a.id FROM StaffWarehouseAssignment a
                  WHERE a.staff.id = :staffId
                    AND a.tenant.id = :tenantId
                    AND a.warehouse.id = b.warehouse.id
                    AND a.status = fu.stockspace.stockspace_be.staff.entity.AssignmentStatus.ACTIVE
                    AND a.isActive = true
                    AND a.isDeleted = false
              )
            """)
    List<StockBatch> findBySkuIdInActiveAssignedTenantWarehouses(
            @Param("skuId") UUID skuId,
            @Param("tenantId") UUID tenantId,
            @Param("staffId") UUID staffId
    );

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM StockBatch b WHERE b.skuId = :skuId AND b.isDeleted = false")
    int sumQuantityBySkuId(@Param("skuId") UUID skuId);

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM StockBatch b WHERE b.bin.id = :binId AND b.isDeleted = false")
    int sumQuantityByBinId(@Param("binId") UUID binId);

    Optional<StockBatch> findByIdAndIsDeletedFalse(UUID id);

    interface WarehouseStockSummaryProjection {
        Long getProductCount();

        Long getBatchCount();

        Long getTotalQuantity();
    }
}
