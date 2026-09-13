package fu.stockspace.stockspace_be.wms.dataexchange.inventory;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Tenant — WMS Inventory Snapshot", description = "Read-only warehouse inventory snapshot export")
@RestController
@RequestMapping("/api/tenant/wms-data/warehouses")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
public class InventorySnapshotExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final InventorySnapshotExportService exportService;

    @GetMapping(value = "/{warehouseId}/inventory-snapshot/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Operation(summary = "Export a warehouse inventory snapshot")
    public ResponseEntity<byte[]> export(@PathVariable UUID warehouseId) {
        byte[] content = exportService.export(TenantContextUtil.getCurrentTenantId(), warehouseId,
                currentStaffIdIfApplicable());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentLength(content.length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stockspace-inventory-snapshot-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    private UUID currentStaffIdIfApplicable() {
        return SecurityUtil.getCurrentUser()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName())))
                .map(user -> user.getId()).orElse(null);
    }
}
