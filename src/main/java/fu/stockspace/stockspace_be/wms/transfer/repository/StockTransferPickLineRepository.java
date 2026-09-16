package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferPickLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StockTransferPickLineRepository extends JpaRepository<StockTransferPickLine, UUID> {
    @Query("select coalesce(sum(p.quantity), 0) from StockTransferPickLine p "
            + "where p.sourceAllocation.id = :allocationId and p.isDeleted = false")
    long sumPickedByAllocation(@Param("allocationId") UUID allocationId);

    @Query("select p.sourceAllocation.id as allocationId, sum(p.quantity) as pickedQuantity "
            + "from StockTransferPickLine p "
            + "where p.sourceAllocation.id in :allocationIds and p.isDeleted = false "
            + "group by p.sourceAllocation.id")
    List<AllocationPickTotal> sumPickedByAllocationIds(
            @Param("allocationIds") Collection<UUID> allocationIds);

    interface AllocationPickTotal {
        UUID getAllocationId();

        Long getPickedQuantity();
    }
}
