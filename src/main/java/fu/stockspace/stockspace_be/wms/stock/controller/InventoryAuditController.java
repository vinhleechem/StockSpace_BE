package fu.stockspace.stockspace_be.wms.stock.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.wms.stock.dto.*;
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
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Tenant — WMS Inventory Audit", description = "Các API quản lý phiếu kiểm kê kho dành cho Tenant")
@RestController
@RequestMapping("/api/tenant/inventory/audits")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INVENTORY_AUDIT_MANAGE')")
public class InventoryAuditController {

    private final InventoryAuditService auditService;

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.UNAUTHENTICATED))
                .getId();
    }

    @PostMapping
    @Operation(summary = "Tạo phiếu kiểm kê mới — tự động snapshot tồn kho hiện tại")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> createAudit(
            @Valid @RequestBody CreateInventoryAuditRequest request
    ) {
        UUID userId = getCurrentUserId();
        InventoryAuditResponse response = auditService.createAudit(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo phiếu kiểm kê thành công", response));
    }

    @GetMapping
    @Operation(summary = "Danh sách phiếu kiểm kê của tôi (phân trang, có thể lọc theo kho)")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryAuditResponse>>> getMyAudits(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<InventoryAuditResponse> response = auditService.getMyAudits(userId, warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phiếu kiểm kê thành công", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phiếu kiểm kê")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> getAuditDetail(@PathVariable UUID id) {
        UUID userId = getCurrentUserId();
        InventoryAuditResponse response = auditService.getAuditDetail(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết phiếu kiểm kê thành công", response));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Nộp kết quả kiểm đếm thực tế — điền actualQuantity cho từng dòng")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> submitAudit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitAuditRequest request
    ) {
        UUID userId = getCurrentUserId();
        InventoryAuditResponse response = auditService.submitAudit(userId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Nộp kết quả kiểm kê thành công", response));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Duyệt phiếu kiểm kê — tự động sinh phiếu điều chỉnh và cập nhật tồn kho")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> approveAudit(@PathVariable UUID id) {
        UUID approverId = getCurrentUserId();
        InventoryAuditResponse response = auditService.approveAudit(approverId, id);
        return ResponseEntity.ok(ApiResponse.success("Duyệt phiếu kiểm kê thành công. Tồn kho đã được điều chỉnh.", response));
    }

    @PatchMapping("/{id}/reject")
    @Operation(summary = "Từ chối phiếu kiểm kê")
    public ResponseEntity<ApiResponse<InventoryAuditResponse>> rejectAudit(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        UUID approverId = getCurrentUserId();
        String reason = body != null ? body.get("reason") : null;
        InventoryAuditResponse response = auditService.rejectAudit(approverId, id, reason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối phiếu kiểm kê thành công", response));
    }
}
