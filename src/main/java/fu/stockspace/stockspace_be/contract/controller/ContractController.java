package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;



/**
 * Controller xử lý các API Rental Contract.
 *
 * Endpoints:
 *   GET   /api/contracts         — Danh sách hợp đồng của mình
 *   GET   /api/contracts/{id}    — Chi tiết hợp đồng
 *   PATCH /api/contracts/{id}/confirm-handover — Xác nhận bàn giao
 */
@Tag(name = "Contract", description = "API quản lý hợp đồng thuê kho")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'TENANT', 'ADMIN')")
public class ContractController {

    private final ContractService contractService;

    /**
     * GET /api/contracts
     * Danh sách hợp đồng của user hiện tại (phân trang).
     * Owner xem hợp đồng liên quan kho mình; Tenant xem hợp đồng mình tham gia.
     */
    @GetMapping
    @Operation(summary = "Danh sách hợp đồng của mình")
    public ResponseEntity<ApiResponse<Page<RentalContractResponse>>> getMyContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User user = getCurrentUser();
        boolean isOwner = user.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_OWNER"));

        Page<RentalContractResponse> result = isOwner
                ? contractService.getMyContractsAsOwner(user.getId(), page, size)
                : contractService.getMyContractsAsTenant(user.getId(), page, size);

        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách hợp đồng thành công", result));
    }

    /**
     * GET /api/contracts/{id}
     * Chi tiết một hợp đồng — chỉ Owner hoặc Tenant liên quan mới xem được.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết hợp đồng thuê kho")
    public ResponseEntity<ApiResponse<RentalContractResponse>> getById(@PathVariable Long id) {
        Long userId = getCurrentUser().getId();
        RentalContractResponse response = contractService.getContractById(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hợp đồng thành công", response));
    }

    /**
     * PATCH /api/contracts/{id}/confirm-handover
     * Xác nhận bàn giao kho.
     * Khi cả Owner và Tenant đều confirm → hợp đồng COMPLETED + kho AVAILABLE.
     */
    @PatchMapping("/{id}/confirm-handover")
    @Operation(summary = "Xác nhận bàn giao kho (Owner / Tenant)")
    public ResponseEntity<ApiResponse<RentalContractResponse>> confirmHandover(@PathVariable Long id) {
        Long userId = getCurrentUser().getId();
        RentalContractResponse response = contractService.confirmHandover(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận bàn giao thành công", response));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
