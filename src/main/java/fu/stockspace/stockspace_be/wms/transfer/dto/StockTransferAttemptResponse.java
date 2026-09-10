package fu.stockspace.stockspace_be.wms.transfer.dto;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferAttemptResponse {
    private UUID id;
    private int sequenceNo;
    private StockTransferAttemptType type;
    private StockTransferAttemptStatus status;
    private WarehouseSummaryResponse sourceWarehouse;
    private WarehouseSummaryResponse destinationWarehouse;
    private TransferActorResponse destinationStaff;
    private int plannedQuantity;
    private int shippedQuantity;
    private int receivedQuantity;
    private String reason;
    private TransferActorResponse createdBy;
    private LocalDateTime startedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime completedAt;
}
