package fu.stockspace.stockspace_be.wms.transfer.dto;

import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
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
public class StockTransferEventResponse {
    private UUID id;
    private UUID attemptId;
    private Integer attemptSequenceNo;
    private StockTransferStatus fromStatus;
    private StockTransferStatus toStatus;
    private String command;
    private TransferActorResponse actor;
    private String reason;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
