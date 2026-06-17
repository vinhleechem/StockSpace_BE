package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.admin.dto.*;
import fu.stockspace.stockspace_be.admin.service.AdminUserManagementService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/**
 * Controller xử lý các API Quản lý Người dùng (User Management) cho Admin.
 *
 * Tất cả endpoints yêu cầu role ADMIN.
 * Base path: /api/admin/users
 */
@Tag(name = "Admin User Management", description = "Các API quản lý Người dùng của Admin")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserManagementService userManagementService;

    // ==================== Query ====================

    /**
     * GET /api/admin/users
     * Lấy danh sách user có phân trang, tìm kiếm và lọc.
     *
     * Query params:
     *   - keyword: tìm theo email / fullName / phone
     *   - roleName: lọc theo tên role (ví dụ: ROLE_OWNER)
     *   - isActive: lọc theo trạng thái (true/false)
     *   - page: số trang (mặc định 0)
     *   - size: số phần tử mỗi trang (mặc định 10)
     *   - sortBy: trường sắp xếp (mặc định: createdAt)
     *   - sortDir: chiều sắp xếp (asc/desc, mặc định: desc)
     */
    @GetMapping
    @Operation(summary = "Lấy danh sách người dùng (phân trang, tìm kiếm, lọc)")
    public ResponseEntity<ApiResponse<PagedUserResponse>> getUsers(
            @Parameter(description = "Từ khóa tìm kiếm (email/tên/SĐT)")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Lọc theo tên role (ví dụ: ROLE_OWNER)")
            @RequestParam(required = false) String roleName,

            @Parameter(description = "Lọc theo trạng thái tài khoản")
            @RequestParam(required = false) Boolean isActive,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        PagedUserResponse result = userManagementService.getUsers(
                keyword, roleName, isActive, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công", result));
    }

    /**
     * GET /api/admin/users/{id}
     * Xem chi tiết thông tin một User.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết thông tin người dùng theo ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable UUID id
    ) {
        UserResponse user = userManagementService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin người dùng thành công", user));
    }

    // ==================== Create ====================

    /**
     * POST /api/admin/users
     * Admin tạo mới tài khoản User với role bất kỳ.
     */
    @PostMapping
    @Operation(summary = "Admin tạo mới tài khoản người dùng")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request
    ) {
        UserResponse user = userManagementService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo tài khoản người dùng thành công", user));
    }

    // ==================== Update ====================

    /**
     * PUT /api/admin/users/{id}
     * Cập nhật thông tin cơ bản của User (fullName, phone).
     */
    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin người dùng (fullName, phone)")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UserResponse user = userManagementService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin người dùng thành công", user));
    }

    // ==================== Status ====================

    /**
     * PATCH /api/admin/users/{id}/activate
     * Kích hoạt tài khoản User (mở khóa).
     */
    @PatchMapping("/{id}/activate")
    @Operation(summary = "Kích hoạt (mở khóa) tài khoản người dùng")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(
            @PathVariable UUID id
    ) {
        UserResponse user = userManagementService.setUserStatus(id, true);
        return ResponseEntity.ok(ApiResponse.success("Kích hoạt tài khoản thành công", user));
    }

    /**
     * PATCH /api/admin/users/{id}/deactivate
     * Khóa tài khoản User.
     */
    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Khóa tài khoản người dùng")
    public ResponseEntity<ApiResponse<UserResponse>> deactivateUser(
            @PathVariable UUID id
    ) {
        UserResponse user = userManagementService.setUserStatus(id, false);
        return ResponseEntity.ok(ApiResponse.success("Khóa tài khoản thành công", user));
    }

    // ==================== Password ====================

    /**
     * PATCH /api/admin/users/{id}/reset-password
     * Admin đặt lại mật khẩu cho User (không cần mật khẩu cũ).
     */
    @PatchMapping("/{id}/reset-password")
    @Operation(summary = "Admin đặt lại mật khẩu cho người dùng")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        userManagementService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công", null));
    }

    // ==================== Delete ====================

    /**
     * DELETE /api/admin/users/{id}
     * Xóa vĩnh viễn User khỏi hệ thống.
     * Không thể xóa tài khoản Admin.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa vĩnh viễn người dùng khỏi hệ thống")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID id
    ) {
        userManagementService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa người dùng thành công", null));
    }
}
