package fu.stockspace.stockspace_be.wms.picking.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboundPickLineResponse(
        UUID stockBatchId,
        UUID skuId,
        String skuCode,
        String skuName,
        LocalDateTime arrivalDate,
        int quantity
) {
}
