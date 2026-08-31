package fu.stockspace.stockspace_be.wms.picking.dto;

import java.util.List;
import java.util.UUID;

public record OutboundPickStopResponse(
        int sequence,
        UUID rackId,
        String rackCode,
        UUID binId,
        String binCode,
        Integer shelfLevel,
        List<OutboundPickLineResponse> lines
) {
}
