package fu.stockspace.stockspace_be.wms.dataexchange.catalog;

import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Tag(name = "Tenant — WMS Catalog Data Exchange", description = "SKU catalog workbook export")
@RestController
@RequestMapping("/api/tenant/wms-data/catalog")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('PRODUCT_MANAGE')")
public class CatalogExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CatalogExportService exportService;

    @GetMapping(value = "/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Export the tenant-visible SKU catalog workbook")
    public ResponseEntity<byte[]> export() {
        byte[] content = exportService.export(TenantContextUtil.getCurrentTenantId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stockspace-catalog-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
