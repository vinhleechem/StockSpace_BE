package fu.stockspace.stockspace_be.wms.dataexchange.job;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WmsImportJobCommand(
        UUID tenantId,
        UUID createdById,
        UUID warehouseId,
        UUID auditId,
        WmsImportType importType,
        String schemaVersion,
        String originalFilename,
        String fileSha256,
        Map<String, Object> contextMetadata,
        List<WmsImportRowInput> rows
) {
}
