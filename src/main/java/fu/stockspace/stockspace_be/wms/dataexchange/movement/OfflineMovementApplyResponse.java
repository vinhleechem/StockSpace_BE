package fu.stockspace.stockspace_be.wms.dataexchange.movement;

import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;

import java.util.List;
import java.util.UUID;

public record OfflineMovementApplyResponse(
        WmsImportJobResponse job,
        List<OfflineMovementReceiptResult> receipts
) {
    public record OfflineMovementReceiptResult(
            String movementRef,
            int sequenceNo,
            UUID receiptId
    ) {
    }
}
