package fu.stockspace.stockspace_be.admin.controller;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawResponse;
import fu.stockspace.stockspace_be.wallet.service.WithdrawService;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@Slf4j
@RestController
@RequestMapping("/api/admin/withdrawals")
@RequiredArgsConstructor
@Tag(name = "Admin — Withdrawals", description = "Các API phê duyệt yêu cầu rút tiền dành cho Admin")
@PreAuthorize("@rbac.hasPermission('ADMIN_WITHDRAWAL_MANAGE')")
public class AdminWithdrawController {
    private final WithdrawService withdrawService;
    @GetMapping
    @Operation(summary = "Xem danh sách tất cả yêu cầu rút tiền (lọc theo status, phân trang)")
    public ResponseEntity<ApiResponse<PagedResponse<WithdrawResponse>>> getAllWithdrawals(
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<WithdrawResponse> response = withdrawService.getAllWithdrawRequests(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu rút tiền thành công", PagedResponse.fromPage(response)));
    }
    @PatchMapping("/{id}/approve")
    @Operation(summary = "Duyệt yêu cầu rút tiền")
    public ResponseEntity<ApiResponse<WithdrawResponse>> approveWithdraw(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveWithdrawRequest request) {
        String notes = request != null ? request.adminNotes() : "Duyệt bởi Admin";
        WithdrawResponse response = withdrawService.approveWithdraw(id, notes);
        return ResponseEntity.ok(ApiResponse.success("Phê duyệt yêu cầu rút tiền thành công", response));
    }
    @PatchMapping("/{id}/reject")
    @Operation(summary = "Từ chối yêu cầu rút tiền")
    public ResponseEntity<ApiResponse<WithdrawResponse>> rejectWithdraw(
            @PathVariable UUID id,
            @RequestBody ResolveWithdrawRequest request) {
        String notes = request != null ? request.adminNotes() : "Từ chối bởi Admin";
        WithdrawResponse response = withdrawService.rejectWithdraw(id, notes);
        return ResponseEntity.ok(ApiResponse.success("Từ chối yêu cầu rút tiền thành công", response));
    }
    public record ResolveWithdrawRequest(String adminNotes) {}
}
