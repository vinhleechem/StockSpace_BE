package fu.stockspace.stockspace_be.staff.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.staff.dto.StaffWorkHistoryResponse;
import fu.stockspace.stockspace_be.staff.service.TenantStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller dành riêng cho tài khoản Staff tự tra cứu thông tin cá nhân / lịch sử làm việc.
 * Base path: /api/staff
 */
@Slf4j
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@Tag(name = "Staff — Self Portal", description = "Các API cá nhân dành cho tài khoản Staff")
@PreAuthorize("hasRole('STAFF')")
public class StaffSelfController {

    private final TenantStaffService staffService;

    @GetMapping("/my-work-history")
    @Operation(summary = "Xem lịch sử làm việc sự nghiệp của bản thân (doanh nghiệp & kho đã/đang phụ trách)")
    public ResponseEntity<ApiResponse<StaffWorkHistoryResponse>> getMyWorkHistory() {
        User staff = getCurrentUser();
        StaffWorkHistoryResponse response = staffService.getStaffWorkHistory(staff.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử làm việc sự nghiệp thành công", response));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
