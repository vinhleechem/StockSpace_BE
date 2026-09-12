package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Tenant — WMS Catalog Data Exchange", description = "SKU catalog import validation and apply")
@RestController
@RequestMapping("/api/tenant/wms-data/catalog")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('PRODUCT_MANAGE') and hasRole('TENANT')")
public class CatalogImportController {

    private final CatalogImportService importService;

    @PostMapping(value = "/imports/validate", consumes = "multipart/form-data")
    @Operation(summary = "Validate a SKU catalog workbook without changing catalog data")
    public ResponseEntity<ApiResponse<WmsImportJobResponse>> validate(@RequestPart("file") MultipartFile file) {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        UUID actorId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Catalog import validated",
                importService.validate(tenantId, actorId, file)));
    }

    @PostMapping("/imports/{jobId}/apply")
    @Operation(summary = "Apply a previously validated SKU catalog workbook atomically")
    public ResponseEntity<ApiResponse<WmsImportJobResponse>> apply(@PathVariable UUID jobId) {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        UUID actorId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success("Catalog import applied",
                importService.apply(tenantId, actorId, jobId)));
    }
}
