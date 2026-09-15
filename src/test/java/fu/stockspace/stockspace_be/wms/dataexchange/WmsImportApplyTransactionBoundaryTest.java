package fu.stockspace.stockspace_be.wms.dataexchange;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.wms.dataexchange.audit.AuditReconciliationImportService;
import fu.stockspace.stockspace_be.wms.dataexchange.catalog.CatalogImportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WmsImportApplyTransactionBoundaryTest {

    @Test
    void applyEntryPointsDoNotOwnAnOuterTransaction() throws Exception {
        Method catalogApply = CatalogImportService.class.getMethod("apply", UUID.class, UUID.class, UUID.class);
        Method auditApply = AuditReconciliationImportService.class.getMethod("apply", UUID.class, UUID.class);

        assertNull(catalogApply.getAnnotation(Transactional.class));
        assertNull(auditApply.getAnnotation(Transactional.class));
    }

    @Test
    void concurrentApplyUsesAConflictContract() {
        assertEquals(HttpStatus.CONFLICT, ErrorCode.WMS_IMPORT_APPLY_IN_PROGRESS.getStatus());
    }
}
