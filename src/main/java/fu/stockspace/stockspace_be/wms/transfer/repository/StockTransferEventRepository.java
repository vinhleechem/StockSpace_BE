package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockTransferEventRepository extends JpaRepository<StockTransferEvent, UUID> {
    List<StockTransferEvent> findByTransferIdOrderByCreatedAtAscIdAsc(UUID transferId);
}
