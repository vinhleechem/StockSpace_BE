package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from StockTransfer t where t.id = :id and t.isDeleted = false")
    Optional<StockTransfer> findByIdForUpdate(@Param("id") UUID id);
}
