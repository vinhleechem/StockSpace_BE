package fu.stockspace.stockspace_be.wms.dataexchange.job;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Tag(name = "Tenant — WMS Data Import Jobs", description = "Import validation status and error workbooks")
@RestController
@RequestMapping("/api/tenant/wms-data/imports")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_READ') or @rbac.hasPermission('PRODUCT_MANAGE')")
public class WmsImportJobController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final WmsImportJobService jobService;

    @GetMapping("/{jobId}")
    @Operation(summary = "Get an import job and capped validation errors")
    public ResponseEntity<ApiResponse<WmsImportJobResponse>> getJob(@PathVariable UUID jobId) {
        WmsImportJobResponse response = jobService.getJob(
                TenantContextUtil.getCurrentTenantId(), SecurityUtil.getCurrentUserId(), jobId);
        return ResponseEntity.ok(ApiResponse.success("Import job loaded", response));
    }

    @GetMapping(value = "/{jobId}/errors.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Download an import error workbook")
    public ResponseEntity<byte[]> downloadErrors(@PathVariable UUID jobId) {
        byte[] content = jobService.renderErrorWorkbook(
                TenantContextUtil.getCurrentTenantId(), SecurityUtil.getCurrentUserId(), jobId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("wms-import-errors-" + jobId + ".xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
