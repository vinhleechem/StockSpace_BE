package fu.stockspace.stockspace_be.wms.dataexchange.audit;

import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditResponse;

public record AuditReconciliationApplyResponse(
        WmsImportJobResponse job,
        InventoryAuditResponse audit
) {
}
