package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.CreateSystemPolicyRequest;
import fu.stockspace.stockspace_be.common.dto.SystemPolicyResponse;
import fu.stockspace.stockspace_be.common.service.SystemPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller dành riêng cho Administrator quản lý chính sách hệ thống (Cam kết ràng buộc).
 */
@Tag(name = "Admin — System Policies Management", description = "Các API quản lý chính sách/cam kết ràng buộc hệ thống của Admin")
@RestController
@RequestMapping("/api/admin/system-policies")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class AdminSystemPolicyController {

    private final SystemPolicyService systemPolicyService;

    /**
     * POST /api/admin/system-policies
     * Admin tạo mới một chính sách hệ thống (phiên bản mới sẽ được active, các bản cũ sẽ tự động deactivate).
     */
    @PostMapping
    @Operation(summary = "Tạo phiên bản cam kết ràng buộc mới (Admin)")
    public ResponseEntity<ApiResponse<SystemPolicyResponse>> createPolicy(
            @Valid @RequestBody CreateSystemPolicyRequest request
    ) {
        SystemPolicyResponse response = systemPolicyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phiên bản chính sách mới thành công. Bản này hiện đã được đặt làm mặc định hiệu lực.", response));
    }

    /**
     * GET /api/admin/system-policies
     * Admin lấy danh sách lịch sử tất cả các phiên bản chính sách (phân trang).
     */
    @GetMapping
    @Operation(summary = "Xem lịch sử tất cả các phiên bản cam kết ràng buộc (Admin)")
    public ResponseEntity<ApiResponse<Page<SystemPolicyResponse>>> getAllPolicies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<SystemPolicyResponse> result = systemPolicyService.getAllPolicies(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch sử chính sách thành công", result));
    }
}
