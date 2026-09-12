package fu.stockspace.stockspace_be.wms.dataexchange.job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WmsImportJobResponse(
        UUID jobId,
        WmsImportType importType,
        WmsImportJobStatus status,
        String schemaVersion,
        String originalFilename,
        String fileSha256,
        String contentSha256,
        Map<String, Object> contextMetadata,
        UUID warehouseId,
        UUID auditId,
        int totalRows,
        int validRows,
        int invalidRows,
        String failureMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime appliedAt,
        List<WmsImportRowErrorResponse> errors
) {
}
