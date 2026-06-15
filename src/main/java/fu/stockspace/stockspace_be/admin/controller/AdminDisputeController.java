package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.DisputeResponse;
import fu.stockspace.stockspace_be.admin.dto.ResolveDisputeRequest;
import fu.stockspace.stockspace_be.admin.service.AdminDisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý các API quản lý và giải quyết tranh chấp dành cho Admin và Inspector.
 */
@Tag(name = "Admin — Dispute Management", description = "Các API giải quyết tranh chấp hợp đồng và phân xử cọc của Admin/Inspector")
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'INSPECTOR')")
public class AdminDisputeController {

    private final AdminDisputeService adminDisputeService;

    /**
     * GET /api/admin/disputes
     * Admin/Inspector xem danh sách toàn bộ các tranh chấp (có phân trang và lọc status).
     */
    @GetMapping
    @Operation(summary = "Xem danh sách các tranh chấp (phân trang, lọc trạng thái)")
    public ResponseEntity<ApiResponse<Page<DisputeResponse>>> getAllDisputes(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<DisputeResponse> result = adminDisputeService.getAllDisputes(status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tranh chấp thành công", result));
    }

    /**
     * POST /api/admin/disputes/{id}/resolve
     * Admin/Inspector giải quyết tranh chấp và phân xử tiền đặt cọc.
     */
    @PostMapping("/{id}/resolve")
    @Operation(summary = "Giải quyết tranh chấp và phân xử tiền đặt cọc")
    public ResponseEntity<ApiResponse<DisputeResponse>> resolve(
            @PathVariable Long id,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        Long adminId = getCurrentUserId();
        DisputeResponse response = adminDisputeService.resolveDispute(id, adminId, request);
        return ResponseEntity.ok(ApiResponse.success("Giải quyết tranh chấp và phân xử tiền cọc thành công", response));
    }

    private Long getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
