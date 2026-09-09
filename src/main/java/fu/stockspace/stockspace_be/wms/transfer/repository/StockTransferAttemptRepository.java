package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferAttemptRepository extends JpaRepository<StockTransferAttempt, UUID> {
    List<StockTransferAttempt> findByTransferIdOrderBySequenceNoAsc(UUID transferId);

    Optional<StockTransferAttempt> findTopByTransferIdOrderBySequenceNoDesc(UUID transferId);

    Optional<StockTransferAttempt> findByTransferIdAndSequenceNo(UUID transferId, int sequenceNo);
}
