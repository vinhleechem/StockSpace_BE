package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservation;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface StockTransferReservationRepository extends JpaRepository<StockTransferReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockTransferReservation r where r.transferItem.transfer.id = :transferId "
            + "and r.status = fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservationStatus.ACTIVE "
            + "and r.isActive = true and r.isDeleted = false "
            + "order by r.sourceStockBatch.id")
    List<StockTransferReservation> findActiveForTransferForUpdate(@Param("transferId") UUID transferId);

    @Query("select coalesce(sum(r.quantity), 0) from StockTransferReservation r "
            + "where r.sourceStockBatch.id = :batchId and r.status = :status "
            + "and r.isActive = true and r.isDeleted = false "
            + "and (:excludeTransferId is null or r.transferItem.transfer.id <> :excludeTransferId)")
    long sumQuantityByBatchAndStatusExcludingTransfer(
            @Param("batchId") UUID batchId,
            @Param("status") StockTransferReservationStatus status,
            @Param("excludeTransferId") UUID excludeTransferId);

    @Query("select coalesce(sum(r.quantity), 0) from StockTransferReservation r "
            + "where r.sourceStockBatch.skuId = :skuId "
            + "and r.sourceStockBatch.warehouse.id = :warehouseId "
            + "and r.status = fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferReservationStatus.ACTIVE "
            + "and r.isActive = true and r.isDeleted = false")
    long sumActiveQuantityBySkuAndWarehouse(@Param("skuId") UUID skuId,
                                            @Param("warehouseId") UUID warehouseId);

    List<StockTransferReservation> findByTransferItemTransferId(UUID transferId);
}
