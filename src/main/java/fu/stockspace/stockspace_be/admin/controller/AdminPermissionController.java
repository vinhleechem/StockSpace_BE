package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.admin.dto.CreatePermissionRequest;
import fu.stockspace.stockspace_be.admin.dto.PermissionResponse;
import fu.stockspace.stockspace_be.admin.service.PermissionManagementService;
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

/**
 * Controller xử lý các API quản lý Quyền hạn (Permissions) của Admin.
 */
@Tag(name = "Admin Permission Management", description = "Các API quản lý Quyền hạn của Admin")
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    private final PermissionManagementService permissionManagementService;

    /**
     * GET /api/admin/permissions
     * Xem tất cả Permissions hiện có.
     */
    @GetMapping
    @Operation(summary = "Xem danh sách tất cả các quyền hạn (Permissions) hiện có")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionManagementService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách quyền hạn thành công", permissions));
    }

    /**
     * POST /api/admin/permissions
     * Tạo mới một Permission.
     */
    @PostMapping
    @Operation(summary = "Tạo mới một quyền hạn (Permission)")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(
            @Valid @RequestBody CreatePermissionRequest request
    ) {
        PermissionResponse permission = permissionManagementService.createPermission(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo quyền hạn thành công", permission));
    }
}
