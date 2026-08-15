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
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;




@Tag(name = "Admin — Dispute Management", description = "Các API giải quyết tranh chấp hợp đồng và phân xử cọc của Admin/Inspector")
@RestController
@RequestMapping("/api/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('DISPUTE_RESOLVE')")
public class AdminDisputeController {

    private final AdminDisputeService adminDisputeService;





    @GetMapping
    @Operation(summary = "Xem danh sách các tranh chấp (phân trang, lọc trạng thái)")
    public ResponseEntity<ApiResponse<PagedResponse<DisputeResponse>>> getAllDisputes(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<DisputeResponse> result = adminDisputeService.getAllDisputes(status, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tranh chấp thành công", PagedResponse.fromPage(result)));
    }





    @PostMapping("/{id}/resolve")
    @Operation(summary = "Giải quyết tranh chấp và phân xử tiền đặt cọc")
    public ResponseEntity<ApiResponse<DisputeResponse>> resolve(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody ResolveDisputeRequest request
    ) {
        java.util.UUID adminId = getCurrentUserId();
        DisputeResponse response = adminDisputeService.resolveDispute(id, adminId, request);
        return ResponseEntity.ok(ApiResponse.success("Giải quyết tranh chấp và phân xử tiền cọc thành công", response));
    }

    private java.util.UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
