package fu.stockspace.stockspace_be.wms.transfer.dto;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {
    private UUID id;
    private String transferNo;
    private StockTransferStatus status;
    private WarehouseSummaryResponse sourceWarehouse;
    private WarehouseSummaryResponse destinationWarehouse;
    /** Current operational destination; destinationWarehouse remains the original request route. */
    private WarehouseSummaryResponse currentDestinationWarehouse;
    private TransferActorResponse sourceStaff;
    private String note;
    private List<StockTransferItemResponse> items;
    private TransferActorResponse createdBy;
    private TransferActorResponse approvedBy;
    private TransferActorResponse receivedBy;
    private TransferActorResponse rejectedBy;
    private TransferActorResponse cancelledBy;
    private String decisionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime rejectedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expectedArrivalAt;
    private LocalDateTime overdueAt;
    private UUID outboundReceiptId;
    private UUID inboundReceiptId;
    private List<StockTransferAttemptResponse> attempts;
}
