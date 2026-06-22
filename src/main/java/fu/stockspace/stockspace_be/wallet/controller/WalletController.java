package fu.stockspace.stockspace_be.wallet.controller;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.wallet.dto.*;
import fu.stockspace.stockspace_be.wallet.service.TransactionService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wallet.service.WithdrawService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Các API liên quan đến Ví, Giao dịch và Yêu cầu Rút tiền")
@PreAuthorize("isAuthenticated()")
public class WalletController {
    private final WalletService walletService;
    private final TransactionService transactionService;
    private final WithdrawService withdrawService;
    @GetMapping
    @Operation(summary = "Xem thông tin ví và số dư hiện tại")
    public ResponseEntity<ApiResponse<WalletResponse>> getWalletInfo() {
        User user = getCurrentUser();
        WalletResponse response = walletService.getWalletInfo(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ví thành công", response));
    }
    @PostMapping("/top-up")
    @Operation(summary = "Tạo yêu cầu nạp tiền (Sinh mã thanh toán & link redirect VNPAY)")
    public ResponseEntity<ApiResponse<TopUpResponse>> topUp(
            @Valid @RequestBody TopUpRequest request,
            jakarta.servlet.http.HttpServletRequest servletRequest
    ) {
        User user = getCurrentUser();
        String ipAddress = getClientIpAddress(servletRequest);
        TopUpResponse response = walletService.createTopUpRequest(user.getId(), request, ipAddress);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo yêu cầu nạp tiền thành công. Vui lòng hoàn tất thanh toán qua link.", response));
    }

    private String getClientIpAddress(jakarta.servlet.http.HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress != null ? ipAddress : "127.0.0.1";
    }
    @GetMapping("/transactions")
    @Operation(summary = "Xem lịch sử giao dịch ví (phân trang)")
    public ResponseEntity<ApiResponse<PagedTransactionResponse>> getMyTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedTransactionResponse response = transactionService.getMyTransactions(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử giao dịch thành công", response));
    }
    @PostMapping("/withdraw")
    @Operation(summary = "Gửi yêu cầu rút tiền về ngân hàng")
    public ResponseEntity<ApiResponse<WithdrawResponse>> withdraw(@Valid @RequestBody WithdrawRequestDto requestDto) {
        User user = getCurrentUser();
        WithdrawResponse response = withdrawService.submitWithdrawRequest(user.getId(), requestDto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Gửi yêu cầu rút tiền thành công. Số tiền đã được tạm giữ.", response));
    }
    @GetMapping("/withdrawals")
    @Operation(summary = "Xem lịch sử các yêu cầu rút tiền của mình (phân trang)")
    public ResponseEntity<ApiResponse<PagedResponse<WithdrawResponse>>> getMyWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<WithdrawResponse> response = withdrawService.getMyWithdrawRequests(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử yêu cầu rút tiền thành công", PagedResponse.fromPage(response)));
    }
    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}
