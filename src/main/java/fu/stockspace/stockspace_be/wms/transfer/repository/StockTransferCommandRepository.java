package fu.stockspace.stockspace_be.wms.transfer.repository;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockTransferCommandRepository extends JpaRepository<StockTransferCommand, UUID> {
    Optional<StockTransferCommand> findByTenantIdAndCommandAndIdempotencyKey(
            UUID tenantId, String command, String idempotencyKey);
}
