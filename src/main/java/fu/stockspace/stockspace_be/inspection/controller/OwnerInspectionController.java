package fu.stockspace.stockspace_be.inspection.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.inspection.dto.InspectionReportResponse;
import fu.stockspace.stockspace_be.inspection.service.InspectionService;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;



/**
 * Controller xử lý các API Kiểm định của Warehouse Owner.
 *
 * Endpoints:
 *   POST /api/owner/inspections          — Gửi yêu cầu kiểm định
 *   GET  /api/owner/inspections          — Xem lịch sử kiểm định (phân trang)
 */
@Tag(name = "Owner — Inspection", description = "API quản lý yêu cầu kiểm định kho bãi của Owner")
@RestController
@RequestMapping("/api/owner/inspections")
@RequiredArgsConstructor
public class OwnerInspectionController {

    private final InspectionService inspectionService;

    /**
     * POST /api/owner/inspections
     * Gửi yêu cầu kiểm định cho Warehouse.
     */
    @PostMapping
    @PreAuthorize("@rbac.hasPermission('INSPECTION_REQUEST')")
    @Operation(summary = "Gửi yêu cầu kiểm định chất lượng kho bãi")
    public ResponseEntity<ApiResponse<InspectionReportResponse>> requestInspection(
            @RequestParam java.util.UUID warehouseId
    ) {
        java.util.UUID ownerId = getCurrentUser().getId();
        InspectionReportResponse response = inspectionService.requestInspection(ownerId, warehouseId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi yêu cầu kiểm định thành công. Đang chờ phân công Inspector.", response));
    }

    /**
     * GET /api/owner/inspections
     * Xem lịch sử kiểm định kho của Owner (phân trang).
     */
    @GetMapping
    @PreAuthorize("@rbac.hasPermission('INSPECTION_READ')")
    @Operation(summary = "Xem lịch sử kiểm định kho của mình")
    public ResponseEntity<ApiResponse<PagedResponse<InspectionReportResponse>>> getMyInspections(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        java.util.UUID ownerId = getCurrentUser().getId();
        Page<InspectionReportResponse> result = inspectionService.getMyInspections(ownerId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử kiểm định thành công", PagedResponse.fromPage(result)));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
