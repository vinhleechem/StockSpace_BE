package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.*;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;














@Tag(name = "Contract", description = "API quản lý hợp đồng thuê kho")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;






    @GetMapping
    @PreAuthorize("@rbac.hasPermission('CONTRACT_READ')")
    @Operation(summary = "Danh sách hợp đồng của mình")
    public ResponseEntity<ApiResponse<PagedResponse<RentalContractResponse>>> getMyContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User user = getCurrentUser();
        boolean isOwner = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_OWNER"));

        Page<RentalContractResponse> result = isOwner
                ? contractService.getMyContractsAsOwner(user.getId(), page, size)
                : contractService.getMyContractsAsTenant(user.getId(), page, size);

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hợp đồng thành công", PagedResponse.fromPage(result)));
    }







    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_READ')")
    @Operation(summary = "Chi tiết hợp đồng thuê kho")
    public ResponseEntity<ApiResponse<RentalContractResponse>> getById(@PathVariable java.util.UUID id) {
        java.util.UUID userId = getCurrentUser().getId();
        RentalContractResponse response = contractService.getContractById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hợp đồng thành công", response));
    }






    @PatchMapping("/{id}/confirm-handover")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_HANDOVER_CONFIRM')")
    @Operation(summary = "Xác nhận bàn giao kho (Owner / Tenant)")
    public ResponseEntity<ApiResponse<RentalContractResponse>> confirmHandover(@PathVariable java.util.UUID id) {
        java.util.UUID userId = getCurrentUser().getId();
        RentalContractResponse response = contractService.confirmHandover(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận bàn giao thành công", response));
    }





    @PostMapping("/{id}/submit-online")
    @Operation(summary = "Owner nộp thông tin & ảnh chụp hợp đồng giấy (Owner)")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    public ResponseEntity<ApiResponse<RentalContractResponse>> submitOnline(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody SubmitContractRequest request
    ) {
        java.util.UUID ownerId = getCurrentUser().getId();
        RentalContractResponse response = contractService.submitOnlineContract(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Nộp thông tin hợp đồng online thành công. Chờ Tenant xác nhận.", response));
    }





    @PostMapping("/{id}/tenant-confirm")
    @Operation(summary = "Tenant xác nhận kích hoạt hợp đồng (Tenant)")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_TENANT_MANAGE')")
    public ResponseEntity<ApiResponse<RentalContractResponse>> tenantConfirm(@PathVariable java.util.UUID id) {
        java.util.UUID tenantId = getCurrentUser().getId();
        RentalContractResponse response = contractService.tenantConfirmContract(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận kích hoạt hợp đồng thành công", response));
    }





    @PostMapping("/{id}/tenant-report-failed")
    @Operation(summary = "Tenant báo cáo deal thất bại / báo lỗi hợp đồng (Tenant)")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_TENANT_MANAGE')")
    public ResponseEntity<ApiResponse<RentalContractResponse>> tenantReportFailed(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody TenantReportFailedRequest request
    ) {
        java.util.UUID tenantId = getCurrentUser().getId();
        RentalContractResponse response = contractService.tenantReportFailed(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi báo cáo tranh chấp lên hệ thống. Inspector sẽ xem xét.", response));
    }





    @PostMapping("/{id}/owner-cancel")
    @Operation(summary = "Owner đề xuất hủy deal thương lượng (Owner)")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    public ResponseEntity<ApiResponse<RentalContractResponse>> ownerCancel(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody OwnerCancelRequest request
    ) {
        java.util.UUID ownerId = getCurrentUser().getId();
        RentalContractResponse response = contractService.ownerRequestCancel(ownerId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Gửi đề xuất hủy deal thành công. Đang chờ Tenant phản hồi.", response));
    }





    @PostMapping("/{id}/tenant-respond-cancel")
    @Operation(summary = "Tenant phản hồi yêu cầu hủy deal (Tenant)")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_TENANT_MANAGE')")
    public ResponseEntity<ApiResponse<RentalContractResponse>> tenantRespondCancel(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody TenantRespondCancelRequest request
    ) {
        java.util.UUID tenantId = getCurrentUser().getId();
        RentalContractResponse response = contractService.tenantRespondCancel(tenantId, id, request.getAgree());
        String msg = request.getAgree() ? "Đã đồng ý hủy deal thương thảo. Đặt cọc đã được hoàn." : "Không đồng ý hủy deal. Hợp đồng đã chuyển sang Tranh chấp.";
        return ResponseEntity.ok(ApiResponse.success(msg, response));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
