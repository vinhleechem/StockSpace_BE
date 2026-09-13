package fu.stockspace.stockspace_be.wms.dataexchange.audit;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wms.dataexchange.job.WmsImportJobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Tenant — WMS Audit Import", description = "Validate and apply blind inventory count workbooks")
@RestController
@RequestMapping("/api/tenant/wms-data/inventory-audits")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_AUDIT_MANAGE')")
public class AuditReconciliationImportController {

    private final AuditReconciliationImportService importService;

    @PostMapping(value = "/{auditId}/count-imports/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate an audit count workbook without changing the audit")
    public ResponseEntity<ApiResponse<WmsImportJobResponse>> validate(
            @PathVariable UUID auditId,
            @RequestParam("file") MultipartFile file) {
        WmsImportJobResponse response = importService.validate(SecurityUtil.getCurrentUserId(), auditId, file);
        return ResponseEntity.ok(ApiResponse.success("Audit count workbook validated", response));
    }

    @PostMapping("/count-imports/{jobId}/apply")
    @Operation(summary = "Apply audit counts without submitting or approving the audit")
    public ResponseEntity<ApiResponse<AuditReconciliationApplyResponse>> apply(@PathVariable UUID jobId) {
        AuditReconciliationApplyResponse response = importService.apply(
                SecurityUtil.getCurrentUserId(), jobId);
        return ResponseEntity.ok(ApiResponse.success("Audit count workbook applied", response));
    }
}
