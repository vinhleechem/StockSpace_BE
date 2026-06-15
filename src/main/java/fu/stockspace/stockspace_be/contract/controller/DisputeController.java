package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.CreateDisputeRequest;
import fu.stockspace.stockspace_be.contract.dto.DisputeResponse;
import fu.stockspace.stockspace_be.contract.service.DisputeService;
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
 * Controller xử lý các API Dispute Ticket.
 *
 * Endpoints:
 *   POST /api/disputes        — Mở tranh chấp
 *   GET  /api/disputes/mine   — Danh sách dispute của mình
 */
@Tag(name = "Dispute", description = "API quản lý tranh chấp hợp đồng")
@RestController
@RequestMapping("/api/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * POST /api/disputes
     * Mở tranh chấp cho hợp đồng.
     */
    @PostMapping
    @Operation(summary = "Mở tranh chấp hợp đồng")
    public ResponseEntity<ApiResponse<DisputeResponse>> raise(
            @Valid @RequestBody CreateDisputeRequest request
    ) {
        Long userId = getCurrentUser().getId();
        DisputeResponse response = disputeService.raiseDispute(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đã mở tranh chấp thành công. Admin sẽ xử lý sớm.", response));
    }

    /**
     * GET /api/disputes/mine
     * Danh sách dispute do mình mở.
     */
    @GetMapping("/mine")
    @Operation(summary = "Xem danh sách tranh chấp của mình")
    public ResponseEntity<ApiResponse<Page<DisputeResponse>>> getMyDisputes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Long userId = getCurrentUser().getId();
        Page<DisputeResponse> result = disputeService.getMyDisputes(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tranh chấp thành công", result));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
