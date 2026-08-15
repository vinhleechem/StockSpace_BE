package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.admin.dto.*;
import fu.stockspace.stockspace_be.admin.service.RoleManagementService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;




@Tag(name = "Admin Role Management", description = "Các API quản lý Vai trò và gán quyền của Admin")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('ADMIN_ROLE_MANAGE')")
public class AdminRoleController {

    private final RoleManagementService roleManagementService;







    @GetMapping("/roles")
    @Operation(summary = "Xem danh sách tất cả các vai trò (Roles) hiện có")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleManagementService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách vai trò thành công", roles));
    }





    @PostMapping("/roles")
    @Operation(summary = "Tạo mới một vai trò (Role)")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request
    ) {
        RoleResponse role = roleManagementService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo vai trò thành công", role));
    }





    @PutMapping("/roles/{id}")
    @Operation(summary = "Chỉnh sửa thông tin vai trò (Role)")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        RoleResponse role = roleManagementService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò thành công", role));
    }





    @DeleteMapping("/roles/{id}")
    @Operation(summary = "Xóa một vai trò (Role) khỏi hệ thống")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable java.util.UUID id
    ) {
        roleManagementService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa vai trò thành công", null));
    }







    @PostMapping("/roles/{id}/permissions")
    @Operation(summary = "Gán thêm một quyền hạn (Permission) vào vai trò (Role)")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissionToRole(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody AssignPermissionRequest request
    ) {
        RoleResponse role = roleManagementService.assignPermissionToRole(id, request);
        return ResponseEntity.ok(ApiResponse.success("Gán quyền cho vai trò thành công", role));
    }





    @DeleteMapping("/roles/{id}/permissions/{permId}")
    @Operation(summary = "Gỡ bỏ quyền hạn (Permission) khỏi vai trò (Role)")
    public ResponseEntity<ApiResponse<RoleResponse>> removePermissionFromRole(
            @PathVariable java.util.UUID id,
            @PathVariable java.util.UUID permId
    ) {
        RoleResponse role = roleManagementService.removePermissionFromRole(id, permId);
        return ResponseEntity.ok(ApiResponse.success("Gỡ quyền khỏi vai trò thành công", role));
    }







    @PostMapping("/users/{userId}/roles")
    @Operation(summary = "Gán thêm vai trò (Role) cho người dùng (User)")
    public ResponseEntity<ApiResponse<Void>> assignRoleToUser(
            @PathVariable java.util.UUID userId,
            @Valid @RequestBody AssignRoleRequest request
    ) {
        roleManagementService.assignRoleToUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Gán vai trò cho người dùng thành công", null));
    }





    @DeleteMapping("/users/{userId}/roles/{roleId}")
    @Operation(summary = "Xóa vai trò (Role) khỏi người dùng (User)")
    public ResponseEntity<ApiResponse<Void>> removeRoleFromUser(
            @PathVariable java.util.UUID userId,
            @PathVariable java.util.UUID roleId
    ) {
        roleManagementService.removeRoleFromUser(userId, roleId);
        return ResponseEntity.ok(ApiResponse.success("Gỡ vai trò khỏi người dùng thành công", null));
    }
}
