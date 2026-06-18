package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.entity.InspectionStatus;
import fu.stockspace.stockspace_be.inspection.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller dành riêng cho Administrator để quản lý và phân công kiểm định kho bãi.
 */
@Tag(name = "Admin — Inspections Management", description = "Các API phân công và quản lý kiểm định kho bãi của Admin")
@RestController
@RequestMapping("/api/admin/inspections")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class AdminInspectionController {

    private final InspectionService inspectionService;

    /**
     * POST /api/admin/inspections/{id}/assign?inspectorId=...
     * Admin gán Inspector cho yêu cầu kiểm định kho bãi.
     */
    @PostMapping("/{id}/assign")
    @Operation(summary = "Phân công Inspector kiểm định kho bãi (Admin)")
    public ResponseEntity<ApiResponse<InspectionReportResponse>> assignInspector(
            @PathVariable java.util.UUID id,
            @Parameter(description = "ID của Inspector được phân công")
            @RequestParam java.util.UUID inspectorId
    ) {
        InspectionReportResponse response = inspectionService.assignInspector(id, inspectorId);
        return ResponseEntity.ok(ApiResponse.success("Phân công Inspector thành công. Trạng thái kiểm định chuyển sang Đang thực hiện.", response));
    }

    /**
     * GET /api/admin/inspections
     * Admin xem danh sách tất cả các yêu cầu kiểm định (có lọc trạng thái và phân trang).
     */
    @GetMapping
    @Operation(summary = "Xem tất cả danh sách yêu cầu kiểm định (Admin)")
    public ResponseEntity<ApiResponse<Page<InspectionReportResponse>>> getAllInspections(
            @Parameter(description = "Lọc theo trạng thái kiểm định (PENDING, IN_PROGRESS, PASSED, FAILED)")
            @RequestParam(required = false) InspectionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<InspectionReportResponse> result = inspectionService.getAllInspections(status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu kiểm định thành công", result));
    }
}
