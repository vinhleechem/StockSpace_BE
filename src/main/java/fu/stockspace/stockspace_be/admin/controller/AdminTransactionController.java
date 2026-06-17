package fu.stockspace.stockspace_be.admin.controller;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wallet.dto.PagedTransactionResponse;
import fu.stockspace.stockspace_be.wallet.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@Slf4j
@RestController
@RequestMapping("/api/admin/transactions")
@RequiredArgsConstructor
@Tag(name = "Admin — Transactions", description = "Các API thống kê và quản lý giao dịch hệ thống")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTransactionController {
    private final TransactionService transactionService;
    @GetMapping
    @Operation(summary = "Xem lịch sử toàn bộ giao dịch của hệ thống (phân trang)")
    public ResponseEntity<ApiResponse<PagedTransactionResponse>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedTransactionResponse response = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy toàn bộ lịch sử giao dịch thành công", response));
    }
}
