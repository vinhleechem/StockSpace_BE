package fu.stockspace.stockspace_be.wms.dataexchange.job;

import java.util.List;
import java.util.Map;

public record WmsImportRowInput(
        String sheetName,
        int rowNumber,
        String groupKey,
        Map<String, Object> normalizedPayload,
        List<Map<String, String>> validationErrors
) {
}
