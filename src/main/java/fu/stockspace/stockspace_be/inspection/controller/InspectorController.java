package fu.stockspace.stockspace_be.inspection.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.dto.SubmitInspectionRequest;
import fu.stockspace.stockspace_be.inspection.service.InspectionService;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;



/**
 * Controller xử lý các API Kiểm định cho Inspector.
 *
 * Endpoints:
 *   GET  /api/inspector/inspections                  — Xem danh sách được phân công
 *   POST /api/inspector/inspections/{id}/report      — Nộp báo cáo kiểm định
 */
@Tag(name = "Inspector — Inspection", description = "API kiểm định kho của Inspector")
@RestController
@RequestMapping("/api/inspector/inspections")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('INSPECTION_EXECUTE')")
public class InspectorController {

    private final InspectionService inspectionService;

    /**
     * GET /api/inspector/inspections
     * Xem danh sách yêu cầu kiểm định được gán (phân trang).
     */
    @GetMapping
    @Operation(summary = "Xem danh sách kiểm định được phân công")
    public ResponseEntity<ApiResponse<PagedResponse<InspectionReportResponse>>> getAssigned(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.util.UUID inspectorId = getCurrentUser().getId();
        Page<InspectionReportResponse> result = inspectionService.getAssignedInspections(inspectorId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách kiểm định thành công", PagedResponse.fromPage(result)));
    }

    /**
     * POST /api/inspector/inspections/{id}/report
     * Inspector nộp kết quả kiểm định.
     * Status PASSED → kho được verify và AVAILABLE.
     */
    @PostMapping("/{id}/report")
    @Operation(summary = "Nộp báo cáo kết quả kiểm định")
    public ResponseEntity<ApiResponse<InspectionReportResponse>> submitReport(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody SubmitInspectionRequest request
    ) {
        java.util.UUID inspectorId = getCurrentUser().getId();
        InspectionReportResponse response = inspectionService.submitReport(inspectorId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Nộp báo cáo kiểm định thành công", response));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
