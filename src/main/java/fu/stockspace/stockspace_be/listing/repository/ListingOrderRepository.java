package fu.stockspace.stockspace_be.listing.repository;

import fu.stockspace.stockspace_be.listing.entity.ListingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ListingOrderRepository extends JpaRepository<ListingOrder, UUID> {

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
}
