package fu.stockspace.stockspace_be.wms.dataexchange.audit;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
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
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Tenant — WMS Audit Count", description = "Blind inventory audit count workbook")
@RestController
@RequestMapping("/api/tenant/wms-data/inventory-audits")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_AUDIT_MANAGE')")
public class AuditCountSheetExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AuditCountSheetExportService exportService;

    @GetMapping(value = "/{auditId}/count-sheet",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Download the current blind audit count sheet")
    public ResponseEntity<byte[]> export(@PathVariable UUID auditId) {
        byte[] content = exportService.export(SecurityUtil.getCurrentUserId(), auditId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stockspace-audit-count-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
