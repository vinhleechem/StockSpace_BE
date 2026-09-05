package fu.stockspace.stockspace_be.wms.stock.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.wms.stock.dto.AddUnexpectedAuditItemRequest;
import fu.stockspace.stockspace_be.wms.stock.dto.AuditReviewReasonRequest;
import fu.stockspace.stockspace_be.wms.stock.dto.CreateInventoryAuditPlanRequest;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.SaveAuditCountsRequest;
import fu.stockspace.stockspace_be.wms.stock.service.InventoryAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Tenant WMS Inventory Audit", description = "Inventory audit APIs")
@RestController
@RequestMapping("/api/tenant/inventory/audits")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_AUDIT_MANAGE')")
public class InventoryAuditController {

    private final InventoryAuditService auditService;

    private UUID currentUserId() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.UNAUTHENTICATED))
                .getId();
    }

    @PostMapping
    @Operation(summary = "Create an inventory audit plan")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> create(
            @Valid @RequestBody CreateInventoryAuditPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Inventory audit plan created",
                auditService.createAuditV2(currentUserId(), request)));
    }

    @GetMapping
    @Operation(summary = "List inventory audits")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryAuditResponse>>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success("Inventory audits loaded",
                auditService.getAuditsV2(currentUserId(), warehouseId, pageable)));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Start an inventory audit and snapshot stock")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> start(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory audit started",
                auditService.startAuditV2(currentUserId(), id)));
    }

    @PutMapping("/{id}/counts")
    @Operation(summary = "Save physical count results")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> saveCounts(
            @PathVariable UUID id, @Valid @RequestBody SaveAuditCountsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Count results saved",
                auditService.saveAuditCountsV2(currentUserId(), id, request)));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit an inventory audit")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory audit submitted",
                auditService.submitAuditV2(currentUserId(), id)));
    }

    @PostMapping("/{id}/unexpected-items")
    @Operation(summary = "Add a physically found SKU missing from the snapshot")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> addUnexpectedItem(
            @PathVariable UUID id, @Valid @RequestBody AddUnexpectedAuditItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Unexpected item added",
                auditService.addUnexpectedItemV2(currentUserId(), id, request)));
    }

    @PostMapping("/{id}/recount")
    @Operation(summary = "Request a recount")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> recount(
            @PathVariable UUID id, @Valid @RequestBody AuditReviewReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Recount requested",
                auditService.requestRecountV2(currentUserId(), id, request.getReason())));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve an inventory audit and adjust stock")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> approve(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory audit approved",
                auditService.approveAuditV2(currentUserId(), id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an inventory audit")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> cancel(
            @PathVariable UUID id,
            @RequestBody(required = false) AuditReviewReasonRequest request) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(ApiResponse.success("Inventory audit cancelled",
                auditService.cancelAuditV2(currentUserId(), id, reason)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get inventory audit details")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Inventory audit loaded",
                auditService.getAuditDetailV2(currentUserId(), id)));
    }
}
