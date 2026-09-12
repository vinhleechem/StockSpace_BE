package fu.stockspace.stockspace_be.wms.dataexchange.movement;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Tenant — WMS Offline Movements", description = "Offline inbound/outbound workbook template and validation")
@RestController
@RequestMapping("/api/tenant/wms-data/warehouses")
@RequiredArgsConstructor
public class OfflineMovementWorkbookController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final OfflineMovementWorkbookService workbookService;

    @GetMapping(value = "/{warehouseId}/offline-movements/template",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Download the offline movement workbook template")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable UUID warehouseId) {
        byte[] content = workbookService.renderTemplate(
                TenantContextUtil.getCurrentTenantId(), SecurityUtil.getCurrentUserId(), warehouseId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stockspace-offline-movements-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @PostMapping(value = "/{warehouseId}/offline-movements/imports/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@rbac.hasAnyPermission('INBOUND_CREATE', 'OUTBOUND_CREATE', 'INVENTORY_READ')")
    @Operation(summary = "Validate an offline movement workbook without creating receipts")
    public ResponseEntity<ApiResponse<WmsImportJobResponse>> validate(
            @PathVariable UUID warehouseId,
            @RequestParam("file") MultipartFile file) {
        WmsImportJobResponse response = workbookService.validate(
                TenantContextUtil.getCurrentTenantId(), SecurityUtil.getCurrentUserId(), warehouseId, file);
        return ResponseEntity.ok(ApiResponse.success("Offline movement workbook validated", response));
    }
}
