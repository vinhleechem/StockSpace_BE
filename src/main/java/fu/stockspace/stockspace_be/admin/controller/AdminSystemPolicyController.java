package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.CreateSystemPolicyRequest;
import fu.stockspace.stockspace_be.common.dto.SystemPolicyResponse;
import fu.stockspace.stockspace_be.common.service.SystemPolicyService;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;




@Tag(name = "Admin — System Policies Management", description = "Các API quản lý chính sách/cam kết ràng buộc hệ thống của Admin")
@RestController
@RequestMapping("/api/admin/system-policies")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('ADMIN_SYSTEM_POLICY_MANAGE')")
public class AdminSystemPolicyController {

    private final SystemPolicyService systemPolicyService;





    @PostMapping
    @Operation(summary = "Tạo phiên bản cam kết ràng buộc mới (Admin)")
    public ResponseEntity<ApiResponse<SystemPolicyResponse>> createPolicy(
            @Valid @RequestBody CreateSystemPolicyRequest request
    ) {
        SystemPolicyResponse response = systemPolicyService.createPolicy(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo phiên bản chính sách mới thành công. Bản này hiện đã được đặt làm mặc định hiệu lực.", response));
    }





    @GetMapping
    @Operation(summary = "Xem lịch sử tất cả các phiên bản cam kết ràng buộc (Admin)")
    public ResponseEntity<ApiResponse<PagedResponse<SystemPolicyResponse>>> getAllPolicies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<SystemPolicyResponse> result = systemPolicyService.getAllPolicies(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch sử chính sách thành công", PagedResponse.fromPage(result)));
    }
}
