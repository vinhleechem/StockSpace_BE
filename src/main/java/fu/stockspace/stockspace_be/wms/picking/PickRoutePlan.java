package fu.stockspace.stockspace_be.wms.picking;

import java.util.List;

public record PickRoutePlan(
        List<PickRouteStop> stops,
        List<String> warnings
) {
}
