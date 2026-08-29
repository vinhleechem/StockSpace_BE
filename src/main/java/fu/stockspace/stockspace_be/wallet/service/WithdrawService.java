package fu.stockspace.stockspace_be.wallet.service;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawRequestDto;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawResponse;
import fu.stockspace.stockspace_be.wallet.entity.*;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import fu.stockspace.stockspace_be.wallet.repository.WithdrawRequestRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {
    private final WithdrawRequestRepository withdrawRequestRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;



    @Transactional
    public WithdrawResponse submitWithdrawRequest(UUID userId, WithdrawRequestDto dto) {

        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException(ErrorCode.INSUFFICIENT_BALANCE);
        }
        wallet.setBalance(wallet.getBalance().subtract(dto.getAmount()));
        walletRepository.save(wallet);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(dto.getAmount())
                .transactionType(TransactionType.WITHDRAWAL)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .status(TransactionStatus.PENDING)
                .referenceId("SYS-WITHDRAW-PENDING-" + UUID.randomUUID())
                .build();
        transaction = transactionRepository.save(transaction);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        WithdrawRequest request = WithdrawRequest.builder()
                .user(user)
                .transaction(transaction)
                .amount(dto.getAmount())
                .bankName(dto.getBankName())
                .bankAccountNumber(dto.getBankAccountNumber())
                .bankAccountHolder(dto.getBankAccountHolder())
                .status(ApprovalStatus.PENDING)
                .build();
        request = withdrawRequestRepository.save(request);
        log.info("Withdraw Service: User {} submitted withdraw request of {} VND. Wallet balance deducted.", userId, dto.getAmount());
        return mapToResponse(request);
    }



    @Transactional(readOnly = true)
    public Page<WithdrawResponse> getMyWithdrawRequests(UUID userId, Pageable pageable) {
        return withdrawRequestRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }



    @Transactional(readOnly = true)
    public Page<WithdrawResponse> getAllWithdrawRequests(ApprovalStatus status, Pageable pageable) {
        if (status != null) {
            return withdrawRequestRepository.findByStatus(status, pageable)
                    .map(this::mapToResponse);
        }
        return withdrawRequestRepository.findAll(pageable)
                .map(this::mapToResponse);
    }



    @Transactional
    public WithdrawResponse approveWithdraw(UUID requestId, String adminNotes) {
        WithdrawRequest request = withdrawRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WITHDRAW_REQUEST_NOT_FOUND));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.WITHDRAW_ALREADY_PROCESSED);
        }

        request.setStatus(ApprovalStatus.APPROVED);
        request.setAdminNotes(adminNotes);

        Transaction transaction = request.getTransaction();
        if (transaction != null) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setReferenceId("SYS-WITHDRAW-SUCCESS-" + UUID.randomUUID());
            transactionRepository.save(transaction);
        }
        request = withdrawRequestRepository.save(request);
        log.info("Withdraw Service: Admin approved withdraw request ID: {}. Transaction confirmed.", requestId);


        try {
            UUID userId = request.getUser().getId();
            String amountStr = request.getAmount().toPlainString();
            notificationService.push(
                    userId,
                    "Yêu cầu rút tiền được duyệt",
                    "Yêu cầu rút tiền " + amountStr + " VNĐ của bạn đã được duyệt thành công.",
                    "WALLET"
            );
        } catch (Exception e) {
            log.warn("Failed to push withdraw approval notification: {}", e.getMessage());
        }

        return mapToResponse(request);
    }



    @Transactional
    public WithdrawResponse rejectWithdraw(UUID requestId, String adminNotes) {
        WithdrawRequest request = withdrawRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WITHDRAW_REQUEST_NOT_FOUND));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.WITHDRAW_ALREADY_PROCESSED);
        }

        UUID userId = request.getUser().getId();
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);

        request.setStatus(ApprovalStatus.REJECTED);
        request.setAdminNotes(adminNotes);

        Transaction transaction = request.getTransaction();
        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setReferenceId("SYS-WITHDRAW-REJECTED-" + UUID.randomUUID());
            transactionRepository.save(transaction);
        }
        request = withdrawRequestRepository.save(request);
        log.info("Withdraw Service: Admin rejected withdraw request ID: {}. Wallet balance refunded.", requestId);
        return mapToResponse(request);
    }
    private WithdrawResponse mapToResponse(WithdrawRequest r) {
        return WithdrawResponse.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .amount(r.getAmount())
                .bankName(r.getBankName())
                .bankAccountNumber(r.getBankAccountNumber())
                .bankAccountHolder(r.getBankAccountHolder())
                .status(r.getStatus())
                .adminNotes(r.getAdminNotes())
                .transactionId(r.getTransaction() != null ? r.getTransaction().getId() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
