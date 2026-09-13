package fu.stockspace.stockspace_be.wms.dataexchange;

import fu.stockspace.stockspace_be.wms.dataexchange.audit.AuditCountSheetExportController;
import fu.stockspace.stockspace_be.wms.dataexchange.audit.AuditReconciliationImportController;
import fu.stockspace.stockspace_be.wms.dataexchange.catalog.CatalogExportController;
import fu.stockspace.stockspace_be.wms.dataexchange.catalog.CatalogImportController;
import fu.stockspace.stockspace_be.wms.dataexchange.inventory.InventorySnapshotExportController;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobController;
import fu.stockspace.stockspace_be.wms.dataexchange.movement.OfflineMovementWorkbookController;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WmsDataExchangeControllerContractTest {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    void keepsCatalogAndSnapshotDownloadContracts() throws Exception {
        assertEquals("/api/tenant/wms-data/catalog",
                CatalogExportController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertGet(CatalogExportController.class.getMethod("export"), "/export", XLSX, false);
        assertEquals("@rbac.hasPermission('PRODUCT_MANAGE')",
                CatalogExportController.class.getAnnotation(PreAuthorize.class).value());

        assertEquals("/api/tenant/wms-data/warehouses",
                InventorySnapshotExportController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertGet(InventorySnapshotExportController.class.getMethod("export", java.util.UUID.class),
                "/{warehouseId}/inventory-snapshot/export", XLSX, false);
        assertEquals("@rbac.hasPermission('INVENTORY_READ')",
                InventorySnapshotExportController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void keepsImportValidationApplyAndJobContracts() throws Exception {
        assertEquals("/api/tenant/wms-data/catalog",
                CatalogImportController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertPost(CatalogImportController.class.getMethod("validate", org.springframework.web.multipart.MultipartFile.class),
                "/imports/validate", true);
        assertPost(CatalogImportController.class.getMethod("apply", java.util.UUID.class),
                "/imports/{jobId}/apply", true);
        assertEquals("@rbac.hasPermission('PRODUCT_MANAGE') and hasRole('TENANT')",
                CatalogImportController.class.getAnnotation(PreAuthorize.class).value());

        assertEquals("/api/tenant/wms-data/imports",
                WmsImportJobController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertGet(WmsImportJobController.class.getMethod("getJob", java.util.UUID.class),
                "/{jobId}", null, true);
        assertGet(WmsImportJobController.class.getMethod("downloadErrors", java.util.UUID.class),
                "/{jobId}/errors.xlsx", XLSX, false);
        assertEquals("@rbac.hasPermission('INVENTORY_READ') or @rbac.hasPermission('PRODUCT_MANAGE')",
                WmsImportJobController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void keepsMovementAndAuditContractsSeparatedByWorkflow() throws Exception {
        assertEquals("/api/tenant/wms-data/warehouses",
                OfflineMovementWorkbookController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertGet(OfflineMovementWorkbookController.class.getMethod("downloadTemplate", java.util.UUID.class),
                "/{warehouseId}/offline-movements/template", XLSX, false);
        assertPost(OfflineMovementWorkbookController.class.getMethod(
                "validate", java.util.UUID.class, org.springframework.web.multipart.MultipartFile.class),
                "/{warehouseId}/offline-movements/imports/validate", true);
        assertPost(OfflineMovementWorkbookController.class.getMethod("apply", java.util.UUID.class),
                "/offline-movements/imports/{jobId}/apply", true);
        assertEquals("@rbac.hasPermission('INVENTORY_UPDATE') and hasRole('TENANT')",
                OfflineMovementWorkbookController.class.getMethod("apply", java.util.UUID.class)
                        .getAnnotation(PreAuthorize.class).value());

        assertEquals("/api/tenant/wms-data/inventory-audits",
                AuditCountSheetExportController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertGet(AuditCountSheetExportController.class.getMethod("export", java.util.UUID.class),
                "/{auditId}/count-sheet", XLSX, false);
        assertEquals("/api/tenant/wms-data/inventory-audits",
                AuditReconciliationImportController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertPost(AuditReconciliationImportController.class.getMethod(
                "validate", java.util.UUID.class, org.springframework.web.multipart.MultipartFile.class),
                "/{auditId}/count-imports/validate", true);
        assertPost(AuditReconciliationImportController.class.getMethod("apply", java.util.UUID.class),
                "/count-imports/{jobId}/apply", true);
    }

    private void assertGet(Method method, String path, String produces, boolean envelope) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertEquals(path, mapping.value()[0]);
        if (produces == null) {
            assertEquals(0, mapping.produces().length);
        } else {
            assertEquals(produces, mapping.produces()[0]);
        }
        assertReturnType(method, envelope);
    }

    private void assertPost(Method method, String path, boolean envelope) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertEquals(path, mapping.value()[0]);
        assertReturnType(method, envelope);
    }

    private void assertReturnType(Method method, boolean envelope) {
        Operation operation = method.getAnnotation(Operation.class);
        assertNotNull(operation, method.toGenericString());
        assertTrue(!operation.summary().isBlank(), method.toGenericString());
        assertEquals(ResponseEntity.class, method.getReturnType());
        String genericType = method.getGenericReturnType().getTypeName();
        if (envelope) {
            assertTrue(genericType.contains("ApiResponse"), genericType);
        } else {
            assertTrue(genericType.contains("byte[]"), genericType);
        }
    }
}
