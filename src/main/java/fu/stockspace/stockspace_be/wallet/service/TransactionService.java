package fu.stockspace.stockspace_be.wallet.service;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
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
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;



    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> getMyTransactions(UUID userId, Pageable pageable) {
        Wallet wallet = walletService.getOrCreateWallet(userId);
        Page<Transaction> page = transactionRepository.findByWalletId(wallet.getId(), pageable);
        return PagedResponse.fromPage(page, this::mapToResponse);
    }



    @Transactional(readOnly = true)
    public PagedResponse<TransactionResponse> getAllTransactions(Pageable pageable) {
        Page<Transaction> page = transactionRepository.findAll(pageable);
        return PagedResponse.fromPage(page, this::mapToResponse);
    }






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
                .listingOrderId(t.getListingOrderId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
