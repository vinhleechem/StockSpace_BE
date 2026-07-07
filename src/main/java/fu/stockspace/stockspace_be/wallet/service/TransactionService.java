package fu.stockspace.stockspace_be.wallet.service;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wallet.dto.PagedTransactionResponse;
import fu.stockspace.stockspace_be.wallet.dto.TransactionResponse;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.Wallet;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    /**
     * Xem lịch sử giao dịch của ví người dùng hiện tại (phân trang).
     */
    @Transactional(readOnly = true)
    public PagedTransactionResponse getMyTransactions(UUID userId, Pageable pageable) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        Page<Transaction> page = transactionRepository.findByWalletId(wallet.getId(), pageable);
        
        List<TransactionResponse> responses = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();
        return PagedTransactionResponse.builder()
                .content(responses)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
    /**
     * API dành cho Admin xem toàn bộ giao dịch hệ thống.
     */
    @Transactional(readOnly = true)
    public PagedTransactionResponse getAllTransactions(Pageable pageable) {
        Page<Transaction> page = transactionRepository.findAll(pageable);
        
        List<TransactionResponse> responses = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();
        return PagedTransactionResponse.builder()
                .content(responses)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    /**
     * Lấy thông tin trạng thái giao dịch theo mã paymentCode.
     * Kiểm tra quyền sở hữu ví của người dùng.
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransactionStatus(UUID userId, String paymentCode) {
        Transaction transaction = transactionRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        if (!transaction.getWallet().getUser().getId().equals(userId)) {
            throw new fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException(ErrorCode.FORBIDDEN);
        }
        
        return mapToResponse(transaction);
    }

    private TransactionResponse mapToResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .amount(t.getAmount())
                .transactionType(t.getTransactionType())
                .paymentMethod(t.getPaymentMethod())
                .status(t.getStatus())
                .paymentCode(t.getPaymentCode())
                .referenceId(t.getReferenceId())
                .bookingId(t.getBookingId())
                .subscriptionId(t.getSubscriptionId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}