package fu.stockspace.stockspace_be.staff.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.staff.dto.InvitationSentResponse;
import fu.stockspace.stockspace_be.staff.dto.InviteStaffRequest;
import fu.stockspace.stockspace_be.staff.dto.StaffMemberResponse;
import fu.stockspace.stockspace_be.staff.service.TenantStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tenant/staffs")
@RequiredArgsConstructor
@Tag(name = "Tenant — Staff Management", description = "Các API quản lý và mời nhân viên kho (Staff) dành cho Tenant")
@PreAuthorize("hasRole('TENANT')")
public class TenantStaffController {

    private final TenantStaffService staffService;

    @PostMapping("/invite")
    @Operation(summary = "Mời nhân viên kho mới qua email")
    public ResponseEntity<ApiResponse<InvitationSentResponse>> inviteStaff(
            @Valid @RequestBody InviteStaffRequest request
    ) {
        User tenant = getCurrentUser();
        InvitationSentResponse response = staffService.sendInvitation(tenant.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi lời mời nhân viên kho thành công", response));
    }

    @GetMapping
    @Operation(summary = "Xem danh sách nhân viên kho của tổ chức")
    public ResponseEntity<ApiResponse<Page<StaffMemberResponse>>> listStaffs(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User tenant = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);
        Page<StaffMemberResponse> response = staffService.listStaffs(tenant.getId(), keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách nhân viên kho thành công", response));
    }

    @DeleteMapping("/{memberId}")
    @Operation(summary = "Xóa/Sa thải nhân viên kho (soft delete)")
    public ResponseEntity<ApiResponse<Void>> removeStaff(@PathVariable UUID memberId) {
        User tenant = getCurrentUser();
        staffService.removeStaff(tenant.getId(), memberId);
        return ResponseEntity.ok(ApiResponse.success("Xóa nhân viên kho khỏi tổ chức thành công", null));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
