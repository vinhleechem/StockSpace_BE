package fu.stockspace.stockspace_be.staff.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.staff.dto.StaffOperationResponse;
import fu.stockspace.stockspace_be.staff.dto.StaffWorkHistoryResponse;
import fu.stockspace.stockspace_be.staff.service.StaffOperationsService;
import fu.stockspace.stockspace_be.staff.service.TenantStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;




@Slf4j
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@Tag(name = "Staff — Self Portal", description = "Các API cá nhân dành cho tài khoản Staff")
@PreAuthorize("@rbac.hasPermission('STAFF_WORK_HISTORY_READ')")
public class StaffSelfController {

    private final TenantStaffService staffService;
    private final StaffOperationsService operationsService;

    @GetMapping("/my-work-history")
    @Operation(summary = "Xem lịch sử làm việc sự nghiệp của bản thân (doanh nghiệp & kho đã/đang phụ trách)")
    public ResponseEntity<ApiResponse<StaffWorkHistoryResponse>> getMyWorkHistory() {
        User staff = getCurrentUser();
        StaffWorkHistoryResponse response = staffService.getStaffWorkHistory(staff.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử làm việc sự nghiệp thành công", response));
    }

    @GetMapping("/operations")
    @Operation(summary = "Xem các operation WMS đang cần xử lý tại kho được phân công")
    public ResponseEntity<ApiResponse<PagedResponse<StaffOperationResponse>>> getOperations(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User staff = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<StaffOperationResponse> response = operationsService.getOperations(
                staff.getId(), TenantContextUtil.getCurrentTenantId(),
                warehouseId, type, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách operation của Staff thành công", response));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
