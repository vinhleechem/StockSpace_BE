package fu.stockspace.stockspace_be.listing.repository;

import fu.stockspace.stockspace_be.listing.entity.ListingOrder;
import fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ListingOrderRepository extends JpaRepository<ListingOrder, UUID> {

    boolean existsByWarehouseIdAndStatusAndIsDeletedFalse(UUID warehouseId, ListingOrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM ListingOrder o
            WHERE o.warehouse.id = :warehouseId
              AND o.status = fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus.PENDING_APPROVAL
              AND o.isDeleted = false
            ORDER BY o.createdAt DESC
            """)
    List<ListingOrder> findPendingByWarehouseIdForUpdate(@Param("warehouseId") UUID warehouseId);

    @Query("""
            SELECT o FROM ListingOrder o
            WHERE o.owner.id = :ownerId
              AND o.warehouse.id = :warehouseId
              AND o.isDeleted = false
            ORDER BY o.createdAt DESC
            """)
    List<ListingOrder> findAllByOwnerIdAndWarehouseId(
            @Param("ownerId") UUID ownerId,
            @Param("warehouseId") UUID warehouseId
    );

    @Query("""
            SELECT o.id AS orderId, o.warehouse.id AS warehouseId, o.status AS status
            FROM ListingOrder o
            WHERE o.warehouse.id IN :warehouseIds
              AND o.isDeleted = false
              AND o.createdAt = (
                    SELECT MAX(latest.createdAt)
                    FROM ListingOrder latest
                    WHERE latest.warehouse.id = o.warehouse.id
                      AND latest.isDeleted = false
              )
            ORDER BY o.createdAt DESC
            """)
    List<LatestListingOrderState> findLatestStateByWarehouseIds(
            @Param("warehouseIds") List<UUID> warehouseIds
    );

    interface LatestListingOrderState {
        UUID getOrderId();

        UUID getWarehouseId();

        ListingOrderStatus getStatus();
    }
}
